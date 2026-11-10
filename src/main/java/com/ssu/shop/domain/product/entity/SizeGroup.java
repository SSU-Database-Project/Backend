package com.ssu.shop.domain.product.entity;

/** 사이즈 체계. 카테고리마다 하나가 정해지고, 상품 옵션은 그 체계의 사이즈만 쓸 수 있다. */
public enum SizeGroup {
    CLOTHING, // XS ~ XXL
    SHOES,    // 220 ~ 300
    FREE      // FREE
}
