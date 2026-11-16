package com.ssu.shop.domain.order.controller;

import com.ssu.shop.domain.order.dto.OrderDtos.OrderCreateRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderResponse;
import com.ssu.shop.domain.order.service.OrderService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order", description = "주문 (일반회원)")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "주문 생성 — 장바구니에서 체크한 항목을 주문 (모의 결제)",
            description = "본문은 배송지만 보낸다. 체크한(selected) 장바구니 항목이 전부 주문되고 장바구니에서 빠진다. "
                    + "판매자마다 배송 묶음(shipments)이 하나씩 생기며 모두 ORDERED 로 시작한다. "
                    + "하나라도 실패하면 아무것도 주문되지 않는다. 오류: NO_ORDER_ITEMS(400) 체크한 항목 없음, "
                    + "PRODUCT_NOT_PURCHASABLE(409) 판매중이 아닌 상품이 체크됨, OUT_OF_STOCK(409) 재고 부족·품절, "
                    + "DATA_CONFLICT(409) 주문하는 사이 장바구니가 바뀜(중복 클릭 등), INVALID_INPUT(400) 배송지 형식")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(@AuthenticationPrincipal LoginUser loginUser,
                                             @Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.success(orderService.create(loginUser.id(), request));
    }
}
