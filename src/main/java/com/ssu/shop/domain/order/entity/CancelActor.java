package com.ssu.shop.domain.order.entity;

/** 배송 묶음을 취소한 사람 (V8). 구매자는 주문완료에서, 판매자는 배송 전(주문완료·배송준비)에 취소할 수 있다. */
public enum CancelActor {
    BUYER, SELLER
}
