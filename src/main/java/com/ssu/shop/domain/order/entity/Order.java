package com.ssu.shop.domain.order.entity;

import com.ssu.shop.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * orders: 주문 헤더 (장바구니에서 한 번 주문 = 1건). ORDER 는 SQL 예약어라 복수형.
 * 배송 상태·취소·구매확정은 판매자별 배송(shipments)에 있다. 주문 헤더에는 상태가 없다.
 * total_price 는 주문 당시 전체 결제 금액(Σ unit_price × quantity). 주문 줄이 생성 후 불변이라 저장하며, 취소돼도 바꾸지 않는다.
 *
 * created_at 은 자동으로 찍지 않고 주문 시각을 직접 넣는다 — 같은 주문의 배송 묶음과 정확히 같은 값이어야
 * 판매자 통계(shipments.created_at 기준)와 플랫폼 통계(orders.created_at 기준)의 날짜가 어긋나지 않는다.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;

    @Column(nullable = false)
    private String address;

    @Column(name = "address_detail")
    private String addressDetail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Order(User user, LocalDateTime orderedAt, long totalPrice, String receiverName, String receiverPhone,
                  String zipCode, String address, String addressDetail) {
        this.user = user;
        this.createdAt = orderedAt;
        this.totalPrice = totalPrice;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
    }

    public static Order create(User user, LocalDateTime orderedAt, long totalPrice, String receiverName,
                               String receiverPhone, String zipCode, String address, String addressDetail) {
        return new Order(user, orderedAt, totalPrice, receiverName, receiverPhone, zipCode, address, addressDetail);
    }
}
