package com.ssu.shop.domain.payment.entity;

import com.ssu.shop.domain.order.entity.Shipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** payment_refunds: 판매자별 배송 묶음 취소 1건 = 부분 환불 1건 (V7). 금액 = 그 묶음의 주문 라인 합계 */
@Entity
@Table(name = "payment_refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "refunded_at", nullable = false)
    private LocalDateTime refundedAt;

    private PaymentRefund(Payment payment, Shipment shipment, long amount, LocalDateTime refundedAt) {
        this.payment = payment;
        this.shipment = shipment;
        this.amount = amount;
        this.refundedAt = refundedAt;
    }

    public static PaymentRefund of(Payment payment, Shipment shipment, long amount, LocalDateTime refundedAt) {
        return new PaymentRefund(payment, shipment, amount, refundedAt);
    }
}
