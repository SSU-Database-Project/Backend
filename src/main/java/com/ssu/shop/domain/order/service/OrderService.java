package com.ssu.shop.domain.order.service;

import com.ssu.shop.domain.cart.entity.CartItem;
import com.ssu.shop.domain.cart.repository.CartItemRepository;
import com.ssu.shop.domain.inventory.service.StockService;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderCreateRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderResponse;
import com.ssu.shop.domain.order.entity.Order;
import com.ssu.shop.domain.order.entity.OrderItem;
import com.ssu.shop.domain.order.entity.Shipment;
import com.ssu.shop.domain.order.repository.OrderItemRepository;
import com.ssu.shop.domain.order.repository.OrderRepository;
import com.ssu.shop.domain.order.repository.ShipmentRepository;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.user.entity.UserStatus;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.domain.user.repository.UserRepository.UserStatusRow;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final StockService stockService;
    private final EntityManager entityManager;
    private final Clock clock;

    /**
     * 주문 생성: 장바구니에서 체크한 항목을 주문한다. 전체가 한 트랜잭션이라 중간에 하나라도 실패하면
     * (재고 부족 등) 주문·재고·재고 이력·장바구니가 모두 주문 전 상태로 돌아간다.
     *
     * 순서
     *  1) 체크한 장바구니 항목 조회 (쿼리 1번)
     *  1-1) 구매자·판매자 회원 행 잠금 + 둘 다 이용 중인지 확인 (이유는 lockAccounts 주석)
     *  2) 상품 행 잠금 + 최신 값으로 다시 읽기 (이유는 lockProducts 주석)
     *  3) 옵션 행 잠금 — 주문 줄을 저장하기 전에 (이유는 StockService.lock 주석)
     *  4) 판매중이 아닌 상품이 섞여 있으면 주문 전체 거부 — 2) 에서 다시 읽은 상태로 검사한다
     *  5) 주문 1건 + 판매자마다 배송 묶음 1개 + 주문 줄 저장 (주문과 배송 묶음의 생성 시각은 같은 값).
     *     주문 줄에 복사하는 상품명·단가와 주문 총액도 2) 에서 다시 읽은 값이다
     *  6) 재고 차감 + 재고 이력 (주문 줄마다 한 번)
     *  7) 주문한 장바구니 항목 삭제
     *
     * 잠그는 순서는 항상 회원(id 오름차순) → 상품(id 오름차순) → 옵션(id 오름차순)이다. 이 순서를 바꾸면 주문끼리, 또는 주문과 상품 변경이
     * 서로를 기다리다 멈출 수 있다.
     */
    @Transactional
    public OrderResponse create(Long userId, OrderCreateRequest request) {
        List<CartItem> cartItems = cartItemRepository.findSelectedForOrder(userId);
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_ORDER_ITEMS);
        }
        lockAccounts(userId, cartItems);
        lockProducts(cartItems);
        stockService.lock(cartItems.stream().map(item -> item.getOption().getId()).toList());

        long totalPrice = 0;
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getOption().getProduct();
            if (product.getStatus() != ProductStatus.ON_SALE) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_PURCHASABLE,
                        "지금 구매할 수 없는 상품입니다: " + product.getName());
            }
            totalPrice += (long) product.getPrice() * cartItem.getQuantity();
        }

        LocalDateTime orderedAt = now();
        Order order = orderRepository.save(Order.create(userRepository.getReferenceById(userId), orderedAt,
                totalPrice, request.receiverName(), request.receiverPhone().replace("-", ""), request.zipCode(),
                request.address(), request.addressDetail()));

        // 판매자(옵션 → 상품 → 브랜드 → 판매자)마다 배송 묶음 하나
        Map<Long, Shipment> shipmentBySeller = new LinkedHashMap<>();
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            Seller seller = cartItem.getOption().getProduct().getSeller();
            Shipment shipment = shipmentBySeller.computeIfAbsent(seller.getId(),
                    id -> shipmentRepository.save(Shipment.create(order, seller)));
            orderItems.add(OrderItem.create(shipment, cartItem.getOption(), cartItem.getQuantity()));
        }
        orderItemRepository.saveAll(orderItems);

        stockService.decrease(orderItems); // 재고가 모자라면 OUT_OF_STOCK → 위에서 저장한 것까지 전부 롤백

        // 주문할 때 읽은 그대로인 항목만 지운다. 하나라도 안 지워지면 그 사이 장바구니가 바뀐 것이다:
        //  - 같은 회원이 주문 버튼을 두 번 눌렀다 → 먼저 끝난 주문이 이미 지움 (중복 주문 방지)
        //  - 주문하는 사이 수량을 바꾸거나 체크를 풀었다 → 사용자가 본 것과 다른 주문이 되지 않게 막는다
        int deleted = 0;
        for (CartItem cartItem : cartItems) {
            deleted += cartItemRepository.deleteIfUnchanged(cartItem.getId(), cartItem.getQuantity());
        }
        if (deleted != cartItems.size()) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "장바구니가 변경되었습니다. 다시 확인하고 주문해 주세요.");
        }
        return OrderResponse.of(order, orderItems);
    }

    /**
     * 내 주문 목록 (최신순). 쿼리는 주문 건수와 상관없이 최대 3번:
     * 주문 한 페이지 → 전체 건수 → 그 페이지 주문들의 주문 줄 전부(배송 묶음·판매자·옵션·상품 포함).
     */
    public PageResponse<OrderResponse> myOrders(Long userId, int page, int size) {
        Page<Order> orders = orderRepository.findByUser_IdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size));
        if (orders.isEmpty()) {
            return PageResponse.of(orders, List.of());
        }
        Map<Long, List<OrderItem>> itemsByOrder = orderItemRepository
                .findAllWithDetailsByOrderIds(orders.map(Order::getId).getContent()).stream()
                .collect(Collectors.groupingBy(item -> item.getShipment().getOrder().getId()));
        return PageResponse.of(orders, order -> OrderResponse.of(order, itemsByOrder.get(order.getId())));
    }

    /** 내 주문 상세. 남의 주문은 없는 것처럼 NOT_FOUND */
    public OrderResponse getMyOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUser_Id(orderId, userId)
                .orElseThrow(() -> BusinessException.notFound("주문"));
        return OrderResponse.of(order, orderItemRepository.findAllWithDetailsByOrderIds(List.of(orderId)));
    }

    /**
     * 구매자 취소: 배송 묶음(판매자) 단위, 주문완료(ORDERED)일 때만.
     * 상태 변경과 그 묶음 전체 줄의 재고 복원이 한 트랜잭션이다. 주문의 total_price 는 바꾸지 않는다.
     *
     * 조건부 UPDATE 가 1 을 돌려준 요청만 재고를 복원하므로, 취소를 동시에 두 번 눌러도 재고는 한 번만 돌아오고
     * 판매자의 "배송준비" 처리와 동시에 와도 둘 중 하나만 성공한다.
     */
    @Transactional
    public OrderResponse cancel(Long userId, Long orderId, Long shipmentId) {
        checkMyShipment(userId, orderId, shipmentId);
        if (shipmentRepository.cancel(shipmentId, now()) == 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "주문완료 상태에서만 취소할 수 있습니다.");
        }
        stockService.restore(orderItemRepository.findAllByShipment_Id(shipmentId));
        return getMyOrder(userId, orderId);
    }

    /** 구매확정: 배송완료(DELIVERED)일 때만. 확정한 뒤에는 더 바꿀 수 없다. */
    @Transactional
    public OrderResponse confirm(Long userId, Long orderId, Long shipmentId) {
        checkMyShipment(userId, orderId, shipmentId);
        if (shipmentRepository.confirm(shipmentId, now()) == 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, "배송완료 상태에서만 구매확정할 수 있습니다.");
        }
        return getMyOrder(userId, orderId);
    }

    /** 배송 묶음이 그 주문에 속하고, 그 주문이 내 것인지 확인한다. 아니면 없는 것처럼 NOT_FOUND */
    private void checkMyShipment(Long userId, Long orderId, Long shipmentId) {
        Shipment shipment = shipmentRepository.findWithOrderById(shipmentId)
                .orElseThrow(() -> BusinessException.notFound("주문"));
        Order order = shipment.getOrder();
        if (!order.getId().equals(orderId) || !order.getUser().getId().equals(userId)) {
            throw BusinessException.notFound("주문");
        }
    }

    /** DB 의 DATETIME(6) 에 맞춰 마이크로초까지만 남긴다 */
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * 구매자와 판매자들의 회원 행을 id 오름차순으로 잠그고(공유 잠금, FOR SHARE) 모두 이용 중(ACTIVE)인지 본다.
     *  - 정지된 판매자의 상품은 주문할 수 없다 (팀 결정, 2026-11-17): 정지된 판매자는 로그인을 못 해 배송할 수 없다.
     *  - 관리자의 정지·탈퇴(AccountLifecycle)는 같은 회원 행을 FOR UPDATE 로 잠근다. 그래서 둘이 동시에 오면 한쪽이 기다린다:
     *    주문이 먼저면 관리자가 이 주문까지 세어 "진행 중인 주문"으로 거절하고, 관리자가 먼저면 여기서 바뀐 상태를 보고 거절한다.
     * 상품보다 먼저 잠근다. 판매자 탈퇴도 회원 → 상품 순서로 잠그므로 서로 기다리다 멈추지 않는다.
     */
    private void lockAccounts(Long buyerId, List<CartItem> cartItems) {
        Set<Long> userIds = new TreeSet<>();
        userIds.add(buyerId);
        Map<Long, String> productNameBySellerUser = new LinkedHashMap<>();
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getOption().getProduct();
            Long sellerUserId = product.getSeller().getUser().getId();
            userIds.add(sellerUserId);
            productNameBySellerUser.putIfAbsent(sellerUserId, product.getName());
        }
        for (UserStatusRow row : userRepository.lockStatuses(userIds)) {
            if (UserStatus.ACTIVE.name().equals(row.getStatus())) {
                continue;
            }
            if (row.getId().equals(buyerId)) {
                throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
            }
            throw new BusinessException(ErrorCode.PRODUCT_NOT_PURCHASABLE,
                    "지금 구매할 수 없는 상품입니다: " + productNameBySellerUser.get(row.getId()));
        }
    }

    /**
     * 주문할 상품 행을 id 오름차순으로 잠그고(공유 잠금, FOR SHARE), 잠근 상태에서 DB 의 최신 값으로 다시 읽는다.
     *
     * 다시 읽는 이유: MySQL 은 일반 SELECT 에 "트랜잭션이 처음 읽은 시점"의 값을 계속 보여 준다. 장바구니를 읽을 때 함께 읽은
     * 상품 상태·이름·가격은 그 시점의 값이라, 이 주문이 잠금을 기다리는 사이 판매자가 품절·삭제·수정(→ 재승인 대기)을 커밋해도
     * 바뀌기 전 값으로 보인다. 잠금 조회만 가장 최근에 커밋된 값을 보여 주고, refresh 가 그 값으로 메모리의 상품을 덮어쓴다.
     * 잠그는 이유: 이 주문이 커밋될 때까지 판매자·관리자가 상품을 바꾸지 못하고 기다리게 한다 → 검사한 상태 그대로 주문이 끝난다.
     * 공유 잠금이라 같은 상품을 주문하는 다른 주문은 여기서 기다리지 않는다 (줄을 서는 곳은 옵션 잠금).
     * id 오름차순인 이유: 주문 하나가 상품 여러 개를 잠그므로, 순서가 주문마다 다르면 상품 변경과 얽혀 서로를 기다릴 수 있다.
     */
    private void lockProducts(List<CartItem> cartItems) {
        Map<Long, Product> products = new TreeMap<>();
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getOption().getProduct();
            products.put(product.getId(), product);
        }
        // 상품마다 SELECT 1번. 주문당 상품은 몇 개라 충분하다
        products.values().forEach(product -> entityManager.refresh(product, LockModeType.PESSIMISTIC_READ));
    }
}
