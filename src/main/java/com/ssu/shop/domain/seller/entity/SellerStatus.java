package com.ssu.shop.domain.seller.entity;

/** 판매자/브랜드 공통 승인 상태. 허용 전이: PENDING → APPROVED | REJECTED, REJECTED → PENDING(재신청) */
public enum SellerStatus {
    PENDING, APPROVED, REJECTED
}
