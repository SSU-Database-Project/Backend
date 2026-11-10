package com.ssu.shop.domain.product.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * PENDING  승인 대기 — 신규 등록, 또는 승인 후 상품 정보를 수정해 재승인을 기다리는 상태 (구매자에게 숨김)
 * REJECTED 관리자 거절 (사유는 reject_reason). 판매자가 수정하면 다시 PENDING
 * ON_SALE  판매중
 * SOLD_OUT 판매자가 수동으로 품절 처리 (옵션 재고가 모두 0인 품절은 조회 시 판정)
 * BLOCKED  관리자 차단 — 판매자는 해제할 수 없다
 * DELETED  판매자 삭제 (승인 불필요). 주문 기록이 참조하므로 행은 남긴다
 */
public enum ProductStatus {
    PENDING, REJECTED, ON_SALE, SOLD_OUT, BLOCKED, DELETED;

    /** 구매자에게 노출되는 상태 (관리자 승인을 거친 상태) */
    public static final Set<ProductStatus> PUBLIC_STATUSES = EnumSet.of(ON_SALE, SOLD_OUT);

    /** 판매자가 판매 상태로 지정할 수 있는 값 (승인된 상품끼리만 전환) */
    public static final Set<ProductStatus> SELLER_SALE_STATUSES = EnumSet.of(ON_SALE, SOLD_OUT);

    public boolean isPublic() {
        return PUBLIC_STATUSES.contains(this);
    }
}
