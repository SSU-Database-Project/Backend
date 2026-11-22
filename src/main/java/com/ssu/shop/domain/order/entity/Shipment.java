package com.ssu.shop.domain.order.entity;

import com.ssu.shop.domain.seller.entity.Seller;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * shipments: 한 주문 안의 판매자별 배송 묶음. UNIQUE(order_id, seller_id).
 * 판매자가 배송 상태를 바꾸고(ORDERED → PREPARING → SHIPPING → DELIVERED),
 * 구매자가 배송완료 후 구매확정(CONFIRMED)하면 더 이상 바꿀 수 없다. 취소도 판매자별(ORDERED 에서만).
 * DB CHECK: CONFIRMED ⇔ confirmed_at 존재, CANCELED ⇔ canceled_at 존재.
 *
 * created_at 에는 주문의 created_at 과 같은 값을 직접 넣는다 (판매자 통계가 이 값을 주문 시각으로 쓴다).
 */
@Entity
@Table(name = "shipments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    /** 취소한 사람 (V8). 취소 상태일 때만 값이 있다 (DB CHECK) */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "canceled_by", length = 10)
    private CancelActor canceledBy;

    /** 취소 사유 (V8). 판매자 취소는 필수 (DB CHECK), 구매자는 선택 */
    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Shipment(Order order, Seller seller) {
        this.order = order;
        this.seller = seller;
        this.status = ShipmentStatus.ORDERED;
        this.createdAt = order.getCreatedAt();
    }

    /** 주문완료(ORDERED) 상태로 만들고, 생성 시각은 주문과 같은 값으로 맞춘다 */
    public static Shipment create(Order order, Seller seller) {
        return new Shipment(order, seller);
    }
}
