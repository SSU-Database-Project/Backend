package com.ssu.shop.domain.inventory.controller;

import com.ssu.shop.domain.inventory.dto.StockDtos.StockChangeRequest;
import com.ssu.shop.domain.inventory.dto.StockDtos.StockHistoryResponse;
import com.ssu.shop.domain.inventory.dto.StockDtos.StockResponse;
import com.ssu.shop.domain.inventory.service.SellerStockService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller - Stock", description = "판매자 옵션 재고 입고 / 조정 / 이력")
@RestController
@RequestMapping("/api/seller/options/{optionId}")
@RequiredArgsConstructor
public class SellerStockController {

    private final SellerStockService sellerStockService;

    @Operation(summary = "옵션 입고 / 조정 (승인 불필요)",
            description = "type=IN: quantity 만큼 입고(1 이상). type=ADJUST: quantity 만큼 조정(늘리면 양수, 줄이면 음수). "
                    + "본인 상품의 옵션만 가능. 오류: PRODUCT_NOT_OWNED(403) 남의 상품, PRODUCT_NOT_EDITABLE(409) 삭제된 상품, "
                    + "INVALID_INPUT(400) 수량 0·입고 수량 음수·조정 결과가 음수")
    @PostMapping("/stock")
    public ApiResponse<StockResponse> change(@AuthenticationPrincipal LoginUser loginUser,
                                             @PathVariable Long optionId,
                                             @Valid @RequestBody StockChangeRequest request) {
        return ApiResponse.success(sellerStockService.change(loginUser.id(), optionId, request));
    }

    @Operation(summary = "옵션 재고 변동 이력 (최신순)",
            description = "type: IN 입고, ORDER 주문 차감, CANCEL 취소 복원, ADJUST 조정. "
                    + "quantityAfter 는 그 변동 직후의 재고. orderItemId 는 ORDER·CANCEL 에만 있다")
    @GetMapping("/stock-history")
    public ApiResponse<PageResponse<StockHistoryResponse>> history(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long optionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(sellerStockService.history(loginUser.id(), optionId, page, size));
    }
}
