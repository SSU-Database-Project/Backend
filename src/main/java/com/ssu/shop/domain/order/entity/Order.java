package com.ssu.shop.domain.order.entity;

import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [BE-2 골격] orders: 주문 헤더 (장바구니에서 한 번 주문 = 1건). ORDER 는 SQL 예약어라 복수형.
 * 배송 상태·취소·구매확정은 판매자별 배송(shipments)에 있다. 주문 헤더에는 상태가 없다.
 * total_price 는 주문 당시 전체 결제 금액(Σ unit_price × quantity). 주문 라인이 생성 후 불변이라 저장한다.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

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
}
