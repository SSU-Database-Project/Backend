package com.ssu.shop.domain.order.entity;

/**
 * 판매자별 배송 상태.
 * ORDERED 주문완료 → PREPARING 배송준비 → SHIPPING 배송중 → DELIVERED 배송완료 → CONFIRMED 구매확정(구매자)
 * CANCELED 취소 (ORDERED 에서만). CONFIRMED / CANCELED 이후에는 변경 불가.
 */
public enum ShipmentStatus {
    ORDERED, PREPARING, SHIPPING, DELIVERED, CONFIRMED, CANCELED
}
