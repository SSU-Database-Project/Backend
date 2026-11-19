package com.ssu.shop.domain.payment.entity;

import com.ssu.shop.domain.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * payments: 주문 1건 = 결제 1건 (V7). 주문 생성과 같은 트랜잭션에서 만들고 이후 바꾸지 않는다.
 * 부분·전액 환불 여부는 저장하지 않고 payment_refunds 합계로 계산한다.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false)
    private Long amount;

    /** 카드 결제일 때만. 카드번호 전체는 저장하지 않는다 */
    @Column(name = "card_last4", length = 4, columnDefinition = "char(4)")
    private String cardLast4;

    @Column(name = "approval_no", nullable = false, length = 30)
    private String approvalNo;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    private Payment(Order order, PaymentMethod method, long amount, String cardLast4, String approvalNo,
                    LocalDateTime paidAt) {
        this.order = order;
        this.method = method;
        this.amount = amount;
        this.cardLast4 = cardLast4;
        this.approvalNo = approvalNo;
        this.paidAt = paidAt;
    }

    public static Payment approved(Order order, PaymentMethod method, long amount, String cardLast4,
                                   String approvalNo, LocalDateTime paidAt) {
        return new Payment(order, method, amount, cardLast4, approvalNo, paidAt);
    }
}
