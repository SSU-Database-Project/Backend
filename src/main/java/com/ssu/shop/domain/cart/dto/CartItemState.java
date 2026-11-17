package com.ssu.shop.domain.cart.dto;

import com.ssu.shop.domain.product.entity.ProductStatus;

/**
 * 장바구니 항목을 지금 살 수 있는지.
 * AVAILABLE   구매 가능 (상품이 판매중이고 옵션 재고가 1 이상)
 * SOLD_OUT    품절 (판매자가 품절 처리했거나 옵션 재고가 0)
 * UNAVAILABLE 판매 종료 (승인 대기·거절·차단·삭제 — 구매자에게 보이지 않는 상품)
 * AVAILABLE 만 합계에 들어가고 주문할 수 있다.
 */
public enum CartItemState {
    AVAILABLE, SOLD_OUT, UNAVAILABLE;

    public static CartItemState of(ProductStatus productStatus, int stock) {
        if (productStatus == ProductStatus.SOLD_OUT) {
            return SOLD_OUT;
        }
        if (productStatus != ProductStatus.ON_SALE) {
            return UNAVAILABLE;
        }
        return stock <= 0 ? SOLD_OUT : AVAILABLE;
    }
}
