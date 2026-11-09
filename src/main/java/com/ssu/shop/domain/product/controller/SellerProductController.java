package com.ssu.shop.domain.product.controller;

import com.ssu.shop.domain.product.dto.ProductDtos.ProductCreateRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductDetailResponse;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductUpdateRequest;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.service.ProductService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller - Product", description = "판매자 상품 등록 / 수정 / 목록")
@RestController
@RequestMapping("/api/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductService productService;

    @Operation(summary = "상품 등록", description = "brandId 선택(본인·승인 브랜드만), 소분류 카테고리만, 초기 재고 함께 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDetailResponse> create(@AuthenticationPrincipal LoginUser loginUser,
                                                     @Valid @RequestBody ProductCreateRequest request) {
        return ApiResponse.success(productService.create(loginUser.id(), request));
    }

    @Operation(summary = "상품 수정 / 판매상태 변경 (본인 상품만)",
            description = "null 필드는 변경 안 함. 브랜드 해제는 removeBrand=true. BLOCKED 상품은 상태 변경 불가")
    @PatchMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> update(@AuthenticationPrincipal LoginUser loginUser,
                                                     @PathVariable Long productId,
                                                     @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.success(productService.update(loginUser.id(), productId, request));
    }

    @Operation(summary = "내 상품 목록 (모든 상태)")
    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> myProducts(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(productService.myProducts(loginUser.id(), status, page, size));
    }
}
