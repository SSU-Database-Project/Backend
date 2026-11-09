package com.readmate.domain.product.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * ON_SALE  판매중
 * SOLD_OUT 판매자가 수동으로 품절 처리 (재고 0 품절은 조회 시 inventory.quantity 로 판정)
 * HIDDEN   판매자가 숨김
 * BLOCKED  관리자가 차단 — 판매자는 해제할 수 없다
 */
public enum ProductStatus {
    ON_SALE, SOLD_OUT, HIDDEN, BLOCKED;

    /** 비회원/일반 사용자에게 노출되는 상태 */
    public static final Set<ProductStatus> PUBLIC_STATUSES = EnumSet.of(ON_SALE, SOLD_OUT);

    /** 판매자가 직접 지정할 수 있는 상태 */
    public static final Set<ProductStatus> SELLER_SETTABLE = EnumSet.of(ON_SALE, SOLD_OUT, HIDDEN);

    public boolean isPublic() {
        return PUBLIC_STATUSES.contains(this);
    }
}
