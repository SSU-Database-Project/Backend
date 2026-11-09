package com.ssu.shop.domain.inventory.entity;

/** IN 입고(+), ORDER 주문(-), CANCEL 주문취소(+), ADJUST 조정(+/-) — 부호는 DB CHECK 로도 강제 */
public enum StockType {
    IN, ORDER, CANCEL, ADJUST
}
