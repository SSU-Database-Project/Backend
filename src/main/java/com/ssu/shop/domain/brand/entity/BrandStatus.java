package com.ssu.shop.domain.brand.entity;

/** 브랜드 승인 상태. 허용 전이: PENDING → APPROVED | REJECTED */
public enum BrandStatus {
    PENDING, APPROVED, REJECTED
}
