package com.ssu.shop.domain.product.controller;

import com.ssu.shop.domain.product.dto.ProductDtos.ProductDetailResponse;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductStatusChangeRequest;
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

@Tag(name = "Admin - Product", description = "전체 상품 조회 / 부적절 상품 차단")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @Operation(summary = "전체 상품 목록 (모든 상태)")
    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> list(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(productService.adminSearch(status, keyword, page, size));
    }

    @Operation(summary = "상품 상태 변경", description = "BLOCKED = 관리자 차단 (판매자가 해제 불가)")
    @PatchMapping("/{productId}/status")
    public ApiResponse<ProductDetailResponse> changeStatus(@PathVariable Long productId,
                                                           @Valid @RequestBody ProductStatusChangeRequest request) {
        return ApiResponse.success(productService.adminChangeStatus(productId, request.status()));
    }
}
