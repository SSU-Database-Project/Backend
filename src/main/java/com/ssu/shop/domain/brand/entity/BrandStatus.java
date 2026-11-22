package com.ssu.shop.domain.brand.entity;

/**
 * 브랜드 상태. 허용 전이: PENDING → APPROVED | REJECTED (관리자),
 * 아무 상태 → DELETED (판매자 본인·관리자, V8). 삭제는 되돌릴 수 없다.
 */
public enum BrandStatus {
    PENDING, APPROVED, REJECTED, DELETED
}
