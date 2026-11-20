package com.ssu.shop.domain.order.controller;

import com.ssu.shop.domain.order.dto.OrderDtos.SellerCancelRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.SellerShipmentResponse;
import com.ssu.shop.domain.order.dto.OrderDtos.ShipmentStatusRequest;
import com.ssu.shop.domain.order.entity.ShipmentStatus;
import com.ssu.shop.domain.order.service.SellerShipmentService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller - Shipment", description = "판매자 주문(배송 묶음) 관리")
@RestController
@RequestMapping("/api/seller/shipments")
@RequiredArgsConstructor
public class SellerShipmentController {

    private final SellerShipmentService sellerShipmentService;

    @Operation(summary = "내 배송 묶음 목록 (최신순)",
            description = "한 주문에서 내 상품만 모은 것이 배송 묶음 1건. status 를 주면 그 상태만 조회 "
                    + "(예: status=ORDERED 새 주문, status=CONFIRMED 구매확정된 주문). 생략하면 전체. "
                    + "items 는 주문 당시 상품명·색상·사이즈·단가, 배송지는 주문에서 가져온다")
    @GetMapping
    public ApiResponse<PageResponse<SellerShipmentResponse>> myShipments(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(sellerShipmentService.myShipments(loginUser.id(), status, page, size));
    }

    @Operation(summary = "배송 상태 변경 (한 단계씩 앞으로)",
            description = "ORDERED → PREPARING → SHIPPING → DELIVERED 순서로 한 단계씩만. 판매자는 취소·구매확정을 할 수 없다. "
                    + "오류: INVALID_ORDER_STATUS(409) 순서가 맞지 않거나 이미 취소·확정됨(구매자 취소와 동시에 온 경우 포함), "
                    + "INVALID_INPUT(400) PREPARING·SHIPPING·DELIVERED 가 아닌 값, NOT_FOUND(404) 내 배송 묶음이 아님")
    @PatchMapping("/{shipmentId}/status")
    public ApiResponse<SellerShipmentResponse> changeStatus(@AuthenticationPrincipal LoginUser loginUser,
                                                            @PathVariable Long shipmentId,
                                                            @Valid @RequestBody ShipmentStatusRequest request) {
        return ApiResponse.success(sellerShipmentService.changeStatus(loginUser.id(), shipmentId, request.status()));
    }

    @Operation(summary = "판매자 취소 — 배송 전(주문완료·배송준비)까지, 사유 필수",
            description = "본문: {\"reason\": \"품절로 출고 불가\"}. 재고가 복원되고 그 묶음 금액만큼 부분 환불 기록이 생긴다. "
                    + "구매자 주문내역에 canceledBy=SELLER 와 사유가 보인다. "
                    + "오류: INVALID_ORDER_STATUS(409) 이미 배송중 이후이거나 취소·확정됨, INVALID_INPUT(400) 사유 없음, "
                    + "NOT_FOUND(404) 내 배송 묶음이 아님")
    @PostMapping("/{shipmentId}/cancel")
    public ApiResponse<SellerShipmentResponse> cancel(@AuthenticationPrincipal LoginUser loginUser,
                                                      @PathVariable Long shipmentId,
                                                      @Valid @RequestBody SellerCancelRequest request) {
        return ApiResponse.success(sellerShipmentService.cancel(loginUser.id(), shipmentId, request.reason()));
    }
}
