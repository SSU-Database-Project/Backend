package com.ssu.shop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import com.ssu.shop.domain.cart.entity.CartItem;
import com.ssu.shop.domain.cart.repository.CartItemRepository;
import com.ssu.shop.domain.category.entity.Category;
import com.ssu.shop.domain.inventory.service.StockService;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderCreateRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.PaymentRequest;
import com.ssu.shop.domain.payment.entity.PaymentMethod;
import com.ssu.shop.domain.payment.service.PaymentService;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderResponse;
import com.ssu.shop.domain.order.dto.OrderDtos.ShipmentResponse;
import com.ssu.shop.domain.order.entity.Order;
import com.ssu.shop.domain.order.entity.OrderItem;
import com.ssu.shop.domain.order.entity.Shipment;
import com.ssu.shop.domain.order.entity.ShipmentStatus;
import com.ssu.shop.domain.order.repository.OrderItemRepository;
import com.ssu.shop.domain.order.repository.OrderRepository;
import com.ssu.shop.domain.order.repository.ShipmentRepository;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductOption;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.entity.SizeGroup;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.Fixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 주문 생성 규칙을 DB 없이 확인한다. 락·롤백·동시 주문은 OrderCreateDbTest 에서 실제 MySQL 로 확인한다. */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    static final long BUYER = 100L;
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T01:00:00Z"), SEOUL); // 서울 10:00
    static final OrderCreateRequest REQUEST =
            new OrderCreateRequest("최구매", "010-2222-0001", "06978", "서울특별시 동작구 상도로 369", "101동",
                    new PaymentRequest(PaymentMethod.CARD, "1234-5678-9012-3456"));

    @Mock
    CartItemRepository cartItemRepository;
    @Mock
    OrderRepository orderRepository;
    @Mock
    ShipmentRepository shipmentRepository;
    @Mock
    OrderItemRepository orderItemRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    StockService stockService;
    @Mock
    EntityManager entityManager;
    @Mock
    PaymentService paymentService;

    OrderService orderService;

    Seller sellerA;
    Seller sellerB;
    Category leaf;
    final AtomicLong ids = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        orderService = new OrderService(cartItemRepository, orderRepository, shipmentRepository, orderItemRepository,
                userRepository, stockService, entityManager, CLOCK, paymentService);
        sellerA = Fixtures.seller(1L, Fixtures.user(1L, "a@test.com"));
        sellerB = Fixtures.seller(2L, Fixtures.user(2L, "b@test.com"));
        leaf = Fixtures.leafCategory(11L, Fixtures.rootCategory(1L, SizeGroup.CLOTHING));
        // 저장하면 id 가 생기는 것을 흉내 낸다
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        lenient().when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        lenient().when(cartItemRepository.deleteIfUnchanged(anyLong(), anyInt())).thenReturn(1);
    }

    @Test
    void 판매자마다_배송_묶음을_하나씩_만들고_주문_당시_값을_복사해_합계를_낸다() {
        CartItem tee = cartItem(sellerA, "티셔츠", 10_000, ProductStatus.ON_SALE, 2);
        CartItem hood = cartItem(sellerA, "후드", 50_000, ProductStatus.ON_SALE, 1);
        CartItem cap = cartItem(sellerB, "모자", 19_000, ProductStatus.ON_SALE, 3);
        when(cartItemRepository.findSelectedForOrder(BUYER)).thenReturn(List.of(tee, hood, cap));

        OrderResponse order = orderService.create(BUYER, REQUEST);

        assertThat(order.totalPrice()).isEqualTo(10_000 * 2 + 50_000 + 19_000 * 3);
        assertThat(order.createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 15, 10, 0));
        assertThat(order.receiverPhone()).isEqualTo("01022220001"); // 하이픈을 빼고 저장
        assertThat(order.shipments()).hasSize(2);
        ShipmentResponse a = order.shipments().get(0);
        assertThat(a.sellerId()).isEqualTo(1L);
        assertThat(a.status()).isEqualTo(ShipmentStatus.ORDERED);
        assertThat(a.subtotal()).isEqualTo(70_000);
        assertThat(a.items()).extracting("productName", "colorName", "sizeLabel", "unitPrice", "quantity")
                .containsExactly(org.assertj.core.api.Assertions.tuple("티셔츠", "블랙", "M", 10_000, 2),
                        org.assertj.core.api.Assertions.tuple("후드", "블랙", "M", 50_000, 1));
        assertThat(order.shipments().get(1).subtotal()).isEqualTo(57_000);

        // 배송 묶음의 생성 시각 = 주문의 생성 시각
        ArgumentCaptor<Shipment> shipments = ArgumentCaptor.forClass(Shipment.class);
        verify(shipmentRepository, org.mockito.Mockito.times(2)).save(shipments.capture());
        assertThat(shipments.getAllValues()).extracting(Shipment::getCreatedAt)
                .containsOnly(LocalDateTime.of(2026, 11, 15, 10, 0));
    }

    @Test
    void 옵션을_먼저_잠그고_주문_줄을_저장한_뒤_한_번에_차감하고_장바구니에서_지운다() {
        CartItem tee = cartItem(sellerA, "티셔츠", 10_000, ProductStatus.ON_SALE, 2);
        CartItem cap = cartItem(sellerB, "모자", 19_000, ProductStatus.ON_SALE, 1);
        when(cartItemRepository.findSelectedForOrder(BUYER)).thenReturn(List.of(tee, cap));

        orderService.create(BUYER, REQUEST);

        InOrder order = inOrder(stockService, orderItemRepository, cartItemRepository);
        order.verify(stockService).lock(List.of(tee.getOption().getId(), cap.getOption().getId()));
        order.verify(orderItemRepository).saveAll(anyList());
        ArgumentCaptor<List<OrderItem>> decreased = ArgumentCaptor.captor();
        order.verify(stockService).decrease(decreased.capture()); // 줄마다가 아니라 목록으로 한 번
        assertThat(decreased.getValue()).hasSize(2);
        // 읽은 그대로인지(수량) 조건을 붙여 한 줄씩 지운다
        order.verify(cartItemRepository).deleteIfUnchanged(tee.getId(), 2);
        order.verify(cartItemRepository).deleteIfUnchanged(cap.getId(), 1);
    }

    @Test
    void 결제는_다른_처리를_모두_마친_뒤_서버가_계산한_주문으로_승인하고_거절되면_예외가_그대로_나간다() {
        CartItem tee = cartItem(sellerA, "티셔츠", 10_000, ProductStatus.ON_SALE, 2);
        when(cartItemRepository.findSelectedForOrder(BUYER)).thenReturn(List.of(tee));
        when(paymentService.pay(any(Order.class), eq(REQUEST.payment()), any()))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_DECLINED, "카드사에서 승인을 거절했습니다."));

        assertThatThrownBy(() -> orderService.create(BUYER, REQUEST))
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_DECLINED);

        InOrder order = inOrder(stockService, cartItemRepository, paymentService);
        order.verify(stockService).decrease(anyList());
        order.verify(cartItemRepository).deleteIfUnchanged(tee.getId(), 2);
        ArgumentCaptor<Order> paid = ArgumentCaptor.forClass(Order.class);
        order.verify(paymentService).pay(paid.capture(), eq(REQUEST.payment()),
                eq(LocalDateTime.of(2026, 11, 15, 10, 0)));
        assertThat(paid.getValue().getTotalPrice()).isEqualTo(20_000L); // 금액은 요청이 아니라 주문 총액
    }

    @Test
    void 정지된_판매자의_상품이_있으면_주문_전체를_거부하고_회원을_상품보다_먼저_잠근다() {
        CartItem tee = cartItem(sellerA, "티셔츠", 10_000, ProductStatus.ON_SALE, 1);
        CartItem cap = cartItem(sellerB, "모자", 19_000, ProductStatus.ON_SALE, 1);
        when(cartItemRepository.findSelectedForOrder(BUYER)).thenReturn(List.of(tee, cap));
        when(userRepository.lockStatuses(java.util.Set.of(BUYER, 1L, 2L)))
                .thenReturn(List.of(statusRow(1L, "ACTIVE"), statusRow(2L, "SUSPENDED"), statusRow(BUYER, "ACTIVE")));

        assertThatThrownBy(() -> orderService.create(BUYER, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("모자")
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_PURCHASABLE);
        verify(stockService, org.mockito.Mockito.never()).lock(anyList());
        verify(orderRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void 주문하는_사이_구매자가_정지되면_ACCOUNT_SUSPENDED() {
        CartItem tee = cartItem(sellerA, "티셔츠", 10_000, ProductStatus.ON_SALE, 1);
        when(cartItemRepository.findSelectedForOrder(BUYER)).thenReturn(List.of(tee));
        when(userRepository.lockStatuses(java.util.Set.of(BUYER, 1L)))
                .thenReturn(List.of(statusRow(1L, "ACTIVE"), statusRow(BUYER, "SUSPENDED")));

        assertThatThrownBy(() -> orderService.create(BUYER, REQUEST))
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
    }

    private static com.ssu.shop.domain.user.repository.UserRepository.UserStatusRow statusRow(Long id, String status) {
        return new com.ssu.shop.domain.user.repository.UserRepository.UserStatusRow() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getStatus() {
                return status;
            }
        };
    }

    @Test
    void 상품을_id_순서로_잠근_뒤에_옵션을_잠근다() {
        CartItem tee = cartItem(sellerA, "티셔츠", 10_000, ProductStatus.ON_SALE, 1);
        CartItem cap = cartItem(sellerB, "모자", 19_000, ProductStatus.ON_SALE, 1);
        CartItem teeAgain = cartItemOf(tee.getOption().getProduct(), 1); // 같은 상품의 다른 옵션
        // 장바구니에는 id 가 큰 상품이 먼저 담겨 있다
        when(cartItemRepository.findSelectedForOrder(BUYER)).thenReturn(List.of(cap, tee, teeAgain));

        orderService.create(BUYER, REQUEST);

        InOrder order = inOrder(entityManager, stockService);
        order.verify(entityManager).refresh(tee.getOption().getProduct(), LockModeType.PESSIMISTIC_READ); // 상품마다 한 번
        order.verify(entityManager).refresh(cap.getOption().getProduct(), LockModeType.PESSIMISTIC_READ);
        order.verify(stockService).lock(anyList());
        verify(entityManager, org.mockito.Mockito.times(2)).refresh(any(Product.class), any(LockModeType.class));
    }

    @Test
    void 판매_상태는_상품을_잠근_뒤_다시_읽은_값으로_검사한다() {
        CartItem tee = cartItem(sellerA, "티셔츠", 10_000, ProductStatus.ON_SALE, 1);
        when(cartItemRepository.findSelectedForOrder(BUYER)).thenReturn(List.of(tee));
        // 장바구니를 읽은 뒤 판매자가 품절 처리했고, 잠금 조회가 그 값을 가져온 상황
        doAnswer(inv -> {
            Product locked = inv.getArgument(0);
            ReflectionTestUtils.setField(locked, "status", ProductStatus.SOLD_OUT);
            return null;
        }).when(entityManager).refresh(any(Product.class), any(LockModeType.class));

        assertError(ErrorCode.PRODUCT_NOT_PURCHASABLE, () -> orderService.create(BUYER, REQUEST));
        verify(orderRepository, never()).save(any());
        verify(stockService, never()).decrease(any());
    }

    @Test
    void 주문_줄의_상품명과_단가와_주문_총액은_상품을_잠근_뒤_다시_읽은_값이다() {
        CartItem tee = cartItem(sellerA, "티셔츠", 10_000, ProductStatus.ON_SALE, 2);
        when(cartItemRepository.findSelectedForOrder(BUYER)).thenReturn(List.of(tee));
        doAnswer(inv -> {
            Product locked = inv.getArgument(0);
            ReflectionTestUtils.setField(locked, "name", "새 이름 티셔츠");
            ReflectionTestUtils.setField(locked, "price", 12_000);
            return null;
        }).when(entityManager).refresh(any(Product.class), any(LockModeType.class));

        OrderResponse order = orderService.create(BUYER, REQUEST);

        assertThat(order.totalPrice()).isEqualTo(24_000);
        assertThat(order.shipments().get(0).items()).extracting("productName", "unitPrice")
                .containsExactly(org.assertj.core.api.Assertions.tuple("새 이름 티셔츠", 12_000));
    }

    @Test
    void 체크한_항목이_없으면_NO_ORDER_ITEMS() {
        when(cartItemRepository.findSelectedForOrder(BUYER)).thenReturn(List.of());

        assertError(ErrorCode.NO_ORDER_ITEMS, () -> orderService.create(BUYER, REQUEST));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void 판매중이_아닌_상품이_하나라도_체크돼_있으면_주문_전체를_거부한다() {
        for (ProductStatus status : new ProductStatus[] {ProductStatus.SOLD_OUT, ProductStatus.PENDING,
                ProductStatus.BLOCKED, ProductStatus.DELETED}) {
            when(cartItemRepository.findSelectedForOrder(BUYER)).thenReturn(List.of(
                    cartItem(sellerA, "티셔츠", 10_000, ProductStatus.ON_SALE, 1),
                    cartItem(sellerA, "후드", 50_000, status, 1)));

            assertError(ErrorCode.PRODUCT_NOT_PURCHASABLE, () -> orderService.create(BUYER, REQUEST));
        }
        verify(orderRepository, never()).save(any());
        verify(stockService, never()).decrease(any());
    }

    @Test
    void 주문하는_사이_장바구니_항목이_바뀌었거나_사라졌으면_DATA_CONFLICT() {
        when(cartItemRepository.findSelectedForOrder(BUYER))
                .thenReturn(List.of(cartItem(sellerA, "티셔츠", 10_000, ProductStatus.ON_SALE, 1)));
        when(cartItemRepository.deleteIfUnchanged(anyLong(), anyInt())).thenReturn(0); // 그 사이 바뀌었거나 이미 지워짐

        assertError(ErrorCode.DATA_CONFLICT, () -> orderService.create(BUYER, REQUEST));
    }

    // ===== helpers =====

    private void assertError(ErrorCode expected, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    /** seller 의 브랜드에 속한 상품(블랙 / M 옵션)을 담은 장바구니 항목 */
    private CartItem cartItem(Seller seller, String productName, int price, ProductStatus status, int quantity) {
        Brand brand = Fixtures.brand(seller.getId() * 10, seller, BrandStatus.APPROVED);
        Product product = Fixtures.product(ids.getAndIncrement(), brand, leaf, status);
        ReflectionTestUtils.setField(product, "name", productName);
        ReflectionTestUtils.setField(product, "price", price);
        return cartItemOf(product, quantity);
    }

    private CartItem cartItemOf(Product product, int quantity) {
        ProductOption option = withId(ProductOption.create(product, Fixtures.color(1L, "블랙"),
                Fixtures.size(3L, SizeGroup.CLOTHING, "M"), 100));
        return withId(CartItem.create(null, option, quantity));
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", ids.getAndIncrement());
        return entity;
    }
}
