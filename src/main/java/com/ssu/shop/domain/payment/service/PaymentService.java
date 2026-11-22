package com.ssu.shop.domain.payment.service;

import com.ssu.shop.domain.order.dto.OrderDtos.PaymentRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.PaymentResponse;
import com.ssu.shop.domain.order.entity.Order;
import com.ssu.shop.domain.order.entity.Shipment;
import com.ssu.shop.domain.payment.entity.Payment;
import com.ssu.shop.domain.payment.entity.PaymentMethod;
import com.ssu.shop.domain.payment.entity.PaymentRefund;
import com.ssu.shop.domain.payment.gateway.PaymentGateway;
import com.ssu.shop.domain.payment.repository.PaymentRefundRepository;
import com.ssu.shop.domain.payment.repository.PaymentRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 모의 결제 (V7). 주문 서비스의 트랜잭션 안에서 불린다.
 *  - pay: 주문 생성의 마지막 단계. 금액은 서버가 계산한 주문 총액이다 (클라이언트는 금액을 보내지 않는다).
 *    승인이 거절되면 예외 → 주문·재고·장바구니까지 전부 롤백된다.
 *  - refund: 판매자별 배송 묶음 취소에서 불린다. 그 묶음 금액만큼 부분 환불 기록을 남긴다.
 *
 * 실제 PG 라면 "PG 는 승인했는데 우리 DB 저장이 실패"하는 경우를 위해 승인 취소 API 를 불러야 한다.
 * 모의 결제사는 외부에 남는 상태가 없어서 롤백만으로 충분하다. 그래서 승인은 다른 검사를 모두 마친 맨 끝에 한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository refundRepository;
    private final PaymentGateway paymentGateway;

    public Payment pay(Order order, PaymentRequest request, LocalDateTime paidAt) {
        String digits = request.cardDigits();
        String approvalNo = paymentGateway.approve(request.method(), digits, order.getTotalPrice());
        String last4 = request.method() == PaymentMethod.CARD ? digits.substring(12) : null;
        return paymentRepository.save(
                Payment.approved(order, request.method(), order.getTotalPrice(), last4, approvalNo, paidAt));
    }

    /** V7 이전 주문(결제 없음)은 환불 기록 없이 넘어간다 */
    public void refund(Long orderId, Shipment shipment, long amount, LocalDateTime refundedAt) {
        paymentRepository.findByOrder_Id(orderId)
                .ifPresent(payment -> refundRepository.save(PaymentRefund.of(payment, shipment, amount, refundedAt)));
    }

    /** 주문 id → 결제 정보 (결제 없는 주문은 빠진다). 주문 수와 상관없이 쿼리 2번 */
    public Map<Long, PaymentResponse> responsesByOrderId(Collection<Long> orderIds) {
        List<Payment> payments = paymentRepository.findAllByOrder_IdIn(orderIds);
        if (payments.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<PaymentRefund>> refunds = refundRepository
                .findAllByPayment_IdInOrderByIdAsc(payments.stream().map(Payment::getId).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getPayment().getId()));
        return payments.stream().collect(Collectors.toMap(p -> p.getOrder().getId(),
                p -> PaymentResponse.of(p, refunds.getOrDefault(p.getId(), List.of())), (a, b) -> a));
    }

    public PaymentResponse responseOf(Payment payment) {
        return PaymentResponse.of(payment, List.of());
    }
}
