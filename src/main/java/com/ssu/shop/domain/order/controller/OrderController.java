package com.ssu.shop.domain.order.controller;

import com.ssu.shop.domain.order.dto.OrderDtos.OrderCreateRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderResponse;
import com.ssu.shop.domain.order.service.OrderService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.ssu.shop.global.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order", description = "주문 (일반회원)")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "주문 생성 — 장바구니에서 체크한 항목을 주문 (모의 결제)",
            description = "본문은 배송지 + 결제 정보(payment: method, 카드면 cardNumber)를 보낸다. 금액은 보내지 않고 서버가 주문 총액으로 승인한다. "
                    + "체크한(selected) 장바구니 항목이 전부 주문되고 장바구니에서 빠진다. "
                    + "판매자마다 배송 묶음(shipments)이 하나씩 생기며 모두 ORDERED 로 시작한다. "
                    + "하나라도 실패하면 아무것도 주문되지 않는다. 오류: NO_ORDER_ITEMS(400) 체크한 항목 없음, "
                    + "PRODUCT_NOT_PURCHASABLE(409) 판매중이 아닌 상품이 체크됨, OUT_OF_STOCK(409) 재고 부족·품절, "
                    + "DATA_CONFLICT(409) 주문하는 사이 장바구니가 바뀜(중복 클릭 등), INVALID_INPUT(400) 배송지·결제 정보 형식, "
                    + "PAYMENT_DECLINED(402) 결제 거절 (시연용: 카드번호가 0000 으로 끝나거나 100만 원 초과)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(@AuthenticationPrincipal LoginUser loginUser,
                                             @Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.success(orderService.create(loginUser.id(), request));
    }

    @Operation(summary = "내 주문 목록 (최신순)",
            description = "주문마다 판매자별 배송 묶음(shipments)과 그 상태, 주문 줄(items)이 들어 있다. "
                    + "totalPrice 는 주문 당시 전체 금액이라 일부가 취소돼도 그대로다 — 취소된 금액은 status=CANCELED 인 묶음의 subtotal")
    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> myOrders(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(orderService.myOrders(loginUser.id(), page, size));
    }

    @Operation(summary = "내 주문 상세", description = "내 주문이 아니면 NOT_FOUND(404)")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> myOrder(@AuthenticationPrincipal LoginUser loginUser,
                                              @PathVariable Long orderId) {
        return ApiResponse.success(orderService.getMyOrder(loginUser.id(), orderId));
    }

    @Operation(summary = "주문 취소 — 판매자별 배송 묶음 단위, 주문완료(ORDERED)일 때만",
            description = "그 배송 묶음의 상품 재고가 복원되고, 그 묶음 금액만큼 부분 환불 기록이 생긴다(payment.refunds). 같은 주문의 다른 판매자 묶음은 그대로다. "
                    + "오류: INVALID_ORDER_STATUS(409) 이미 배송준비 이후이거나 취소됨, NOT_FOUND(404) 내 주문이 아님")
    @PostMapping("/{orderId}/shipments/{shipmentId}/cancel")
    public ApiResponse<OrderResponse> cancel(@AuthenticationPrincipal LoginUser loginUser,
                                             @PathVariable Long orderId, @PathVariable Long shipmentId) {
        return ApiResponse.success(orderService.cancel(loginUser.id(), orderId, shipmentId));
    }

    @Operation(summary = "구매확정 — 배송완료(DELIVERED)일 때만",
            description = "확정하면 CONFIRMED 가 되고 더 이상 바꿀 수 없다. "
                    + "오류: INVALID_ORDER_STATUS(409) 배송완료 전이거나 이미 확정·취소됨, NOT_FOUND(404) 내 주문이 아님")
    @PostMapping("/{orderId}/shipments/{shipmentId}/confirm")
    public ApiResponse<OrderResponse> confirm(@AuthenticationPrincipal LoginUser loginUser,
                                              @PathVariable Long orderId, @PathVariable Long shipmentId) {
        return ApiResponse.success(orderService.confirm(loginUser.id(), orderId, shipmentId));
    }
}
