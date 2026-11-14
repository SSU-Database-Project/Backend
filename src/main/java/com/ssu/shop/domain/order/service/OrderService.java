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
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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

        // DB 의 DATETIME(6) 에 맞춰 마이크로초까지만 남긴다 → 응답의 시각과 저장된 시각이 같다
        LocalDateTime orderedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
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
}
