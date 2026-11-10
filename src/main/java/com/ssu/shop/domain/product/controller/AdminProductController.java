package com.ssu.shop.domain.product.controller;

import com.ssu.shop.domain.product.dto.ProductDtos.AdminProductStatusRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductDetailResponse;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.service.ProductService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Product", description = "상품 승인 / 거절 / 차단")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @Operation(summary = "전체 상품 목록", description = "status=PENDING 이면 승인 대기 목록 (먼저 신청한 순)")
    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> list(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(productService.adminSearch(status, keyword, page, size));
    }

    @Operation(summary = "상품 상세 (모든 상태)")
    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> detail(@PathVariable Long productId) {
        return ApiResponse.success(productService.adminDetail(productId));
    }

    @Operation(summary = "승인 / 거절 / 차단 / 차단 해제",
            description = "ON_SALE: 승인(PENDING→) 또는 차단 해제(BLOCKED→), REJECTED: 거절(PENDING→, 사유), "
                    + "BLOCKED: 차단(ON_SALE·SOLD_OUT→)")
    @PatchMapping("/{productId}/status")
    public ApiResponse<ProductDetailResponse> changeStatus(@PathVariable Long productId,
                                                           @Valid @RequestBody AdminProductStatusRequest request) {
        return ApiResponse.success(productService.adminChangeStatus(productId, request));
    }
}
