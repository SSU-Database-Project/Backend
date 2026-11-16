package com.ssu.shop.domain.cart.dto;

import com.ssu.shop.domain.cart.entity.CartItem;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductOption;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    /** 한 옵션을 담을 수 있는 최대 수량 */
    public static final int MAX_QUANTITY = 99;

    /** 담기. 이미 담긴 옵션이면 수량이 늘고 다시 체크된다 (합쳐서 99 를 넘으면 거부). 재고보다 많이 담는 것은 허용한다. */
    public record CartItemAddRequest(
            @NotNull Long optionId,
            @NotNull @Min(1) @Max(MAX_QUANTITY) Integer quantity) {
    }

    /** 수량·체크 변경. null 인 필드는 바꾸지 않는다. */
    public record CartItemUpdateRequest(
            @Min(1) @Max(MAX_QUANTITY) Integer quantity,
            Boolean selected) {
    }

    /**
     * 장바구니 한 줄. price·stock·state 는 조회 시점의 현재 값이다 (장바구니는 가격을 저장하지 않는다).
     * lineTotal = price × quantity.
     */
    public record CartItemResponse(
            Long cartItemId,
            Long optionId,
            Long productId,
            String productName,
            String brandName,
            String imageUrl,
            String colorName,
            String sizeLabel,
            int price,
            int stock,
            CartItemState state,
            int quantity,
            boolean selected,
            long lineTotal) {

        public static CartItemResponse from(CartItem item) {
            ProductOption option = item.getOption();
            Product product = option.getProduct();
            return new CartItemResponse(item.getId(), option.getId(), product.getId(), product.getName(),
                    product.getBrand().getName(), product.getImageUrl(),
                    option.getColor().getName(), option.getSize().getLabel(),
                    product.getPrice(), option.getStockQuantity(),
                    CartItemState.of(product.getStatus(), option.getStockQuantity()),
                    item.getQuantity(), item.isSelected(), (long) product.getPrice() * item.getQuantity());
        }

        /** 합계에 들어가는 줄: 체크되어 있고 구매 가능한 것 */
        public boolean countsInTotal() {
            return selected && state == CartItemState.AVAILABLE;
        }
    }

    /** selectedCount·totalPrice 는 체크되어 있고 구매 가능한(AVAILABLE) 줄만 센다 */
    public record CartResponse(List<CartItemResponse> items, int selectedCount, long totalPrice) {

        public static CartResponse of(List<CartItem> cartItems) {
            List<CartItemResponse> items = cartItems.stream().map(CartItemResponse::from).toList();
            List<CartItemResponse> counted = items.stream().filter(CartItemResponse::countsInTotal).toList();
            return new CartResponse(items, counted.size(),
                    counted.stream().mapToLong(CartItemResponse::lineTotal).sum());
        }
    }
}
