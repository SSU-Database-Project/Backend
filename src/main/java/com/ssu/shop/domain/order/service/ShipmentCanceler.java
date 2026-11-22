package com.ssu.shop.domain.order.service;

import com.ssu.shop.domain.inventory.service.StockService;
import com.ssu.shop.domain.order.entity.CancelActor;
import com.ssu.shop.domain.order.entity.OrderItem;
import com.ssu.shop.domain.order.entity.ShipmentStatus;
import com.ssu.shop.domain.order.repository.OrderItemRepository;
import com.ssu.shop.domain.order.repository.ShipmentRepository;
import com.ssu.shop.domain.payment.service.PaymentService;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 배송 묶음 취소 (구매자·판매자 공통). 호출하는 쪽의 트랜잭션 안에서:
 *  1) 조건부 UPDATE 로 상태 → CANCELED (+ 취소 시각·취소한 사람·사유). from 상태가 아니면 0 행 → 거절
 *  2) 그 묶음 주문 라인의 재고 복원 + CANCEL 이력
 *  3) 그 묶음 금액만큼 부분 환불 기록 (V7, 결제가 있는 주문만). 환불 시각 = 취소 시각
 *
 * 1) 이 1 을 돌려준 요청만 2)·3) 을 하므로, 구매자와 판매자가 동시에 취소하거나 같은 버튼을 두 번 눌러도
 * 재고 복원과 환불은 한 번뿐이다 (DB 도 uk_payment_refunds_shipment 로 막는다).
 */
@Component
@RequiredArgsConstructor
public class ShipmentCanceler {

    private final ShipmentRepository shipmentRepository;
    private final OrderItemRepository orderItemRepository;
    private final StockService stockService;
    private final PaymentService paymentService;
    private final Clock clock;

    public void cancel(Long orderId, Long shipmentId, Set<ShipmentStatus> from, CancelActor by, String reason,
                       String rejectMessage) {
        LocalDateTime canceledAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (shipmentRepository.cancel(shipmentId, from, canceledAt, by, reason) == 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS, rejectMessage);
        }
        List<OrderItem> items = orderItemRepository.findAllByShipment_Id(shipmentId);
        stockService.restore(items);
        paymentService.refund(orderId, shipmentRepository.getReferenceById(shipmentId),
                items.stream().mapToLong(OrderItem::lineTotal).sum(), canceledAt);
    }
}
