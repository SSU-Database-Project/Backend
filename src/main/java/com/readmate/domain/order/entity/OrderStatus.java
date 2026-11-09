package com.readmate.domain.order.entity;

/** 모의 결제: 주문 생성 = 결제 완료(PAID). 취소는 PAID 에서만 가능. */
public enum OrderStatus {
    PAID, SHIPPING, DELIVERED, CANCELED
}
