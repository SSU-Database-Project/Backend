package com.ssu.shop.domain.cart.controller;

import com.ssu.shop.domain.cart.dto.CartDtos.CartItemAddRequest;
import com.ssu.shop.domain.cart.dto.CartDtos.CartItemUpdateRequest;
import com.ssu.shop.domain.cart.dto.CartDtos.CartResponse;
import com.ssu.shop.domain.cart.service.CartService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cart", description = "장바구니 (일반회원). 담기·변경·삭제도 바뀐 장바구니 전체를 돌려준다")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "내 장바구니",
            description = "items[].price·stock·state 는 지금 시점의 값. state: AVAILABLE 구매 가능, SOLD_OUT 품절, "
                    + "UNAVAILABLE 판매 종료. selectedCount·totalPrice 는 체크되어 있고(selected) AVAILABLE 인 줄만 계산. "
                    + "quantity 가 stock 보다 클 수 있다(담을 때는 재고를 검사하지 않음) → 주문하면 OUT_OF_STOCK")
    @GetMapping
    public ApiResponse<CartResponse> getCart(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(cartService.getCart(loginUser.id()));
    }

    @Operation(summary = "옵션 담기 (같은 옵션이면 수량만 증가)",
            description = "quantity 1~99. 이미 담긴 옵션이면 수량을 더하고, 합쳐서 99 를 넘으면 INVALID_INPUT(400). "
                    + "재고보다 많이 담을 수 있다. 없는 옵션이거나 구매자에게 보이지 않는 상품이면 NOT_FOUND(404)")
    @PostMapping("/items")
    public ApiResponse<CartResponse> add(@AuthenticationPrincipal LoginUser loginUser,
                                         @Valid @RequestBody CartItemAddRequest request) {
        return ApiResponse.success(cartService.add(loginUser.id(), request));
    }

    @Operation(summary = "수량 / 체크 상태 변경",
            description = "null 인 필드는 바꾸지 않는다. quantity 1~99, selected 는 체크 여부(다시 들어와도 유지). "
                    + "내 장바구니 항목이 아니면 NOT_FOUND(404)")
    @PatchMapping("/items/{cartItemId}")
    public ApiResponse<CartResponse> update(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable Long cartItemId,
                                            @Valid @RequestBody CartItemUpdateRequest request) {
        return ApiResponse.success(cartService.update(loginUser.id(), cartItemId, request));
    }

    @Operation(summary = "항목 삭제", description = "내 장바구니 항목이 아니면 NOT_FOUND(404)")
    @DeleteMapping("/items/{cartItemId}")
    public ApiResponse<CartResponse> remove(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable Long cartItemId) {
        return ApiResponse.success(cartService.remove(loginUser.id(), cartItemId));
    }
}
