package com.ssu.shop.domain.cart.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssu.shop.domain.product.entity.ProductStatus;
import org.junit.jupiter.api.Test;

class CartItemStateTest {

    @Test
    void 판매중이고_재고가_있으면_구매_가능() {
        assertThat(CartItemState.of(ProductStatus.ON_SALE, 1)).isEqualTo(CartItemState.AVAILABLE);
    }

    @Test
    void 판매자가_품절_처리했거나_옵션_재고가_0이면_품절() {
        assertThat(CartItemState.of(ProductStatus.SOLD_OUT, 5)).isEqualTo(CartItemState.SOLD_OUT);
        assertThat(CartItemState.of(ProductStatus.ON_SALE, 0)).isEqualTo(CartItemState.SOLD_OUT);
    }

    @Test
    void 구매자에게_보이지_않는_상품은_재고와_상관없이_판매_종료() {
        for (ProductStatus hidden : new ProductStatus[] {ProductStatus.PENDING, ProductStatus.REJECTED,
                ProductStatus.BLOCKED, ProductStatus.DELETED}) {
            assertThat(CartItemState.of(hidden, 5)).as(hidden.name()).isEqualTo(CartItemState.UNAVAILABLE);
            assertThat(CartItemState.of(hidden, 0)).as(hidden.name()).isEqualTo(CartItemState.UNAVAILABLE);
        }
    }
}
