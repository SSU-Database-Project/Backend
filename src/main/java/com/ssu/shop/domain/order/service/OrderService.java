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
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final Clock clock;

    /**
     * 주문 생성: 장바구니에서 체크한 항목을 주문한다. 전체가 한 트랜잭션이라 중간에 하나라도 실패하면
     * (재고 부족 등) 주문·재고·재고 이력·장바구니가 모두 주문 전 상태로 돌아간다.
     *
     * 순서
     *  1) 체크한 장바구니 항목 조회 (쿼리 1번)
     *  2) 옵션 행 잠금 — 주문 줄을 저장하기 전에 (이유는 StockService.lock 주석)
     *  3) 판매중이 아닌 상품이 섞여 있으면 주문 전체 거부
     *  4) 주문 1건 + 판매자마다 배송 묶음 1개 + 주문 줄 저장 (주문과 배송 묶음의 생성 시각은 같은 값)
     *  5) 재고 차감 + 재고 이력 (주문 줄마다 한 번)
     *  6) 주문한 장바구니 항목 삭제
     */
    @Transactional
    public OrderResponse create(Long userId, OrderCreateRequest request) {
        List<CartItem> cartItems = cartItemRepository.findSelectedForOrder(userId);
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_ORDER_ITEMS);
        }
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

        // 같은 회원이 주문 버튼을 두 번 눌러 두 요청이 같은 장바구니를 읽었다면, 늦은 쪽은 지울 항목이 이미 없다 → 중복 주문을 막는다
        int deleted = cartItemRepository.deleteByIds(cartItems.stream().map(CartItem::getId).toList());
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
}
