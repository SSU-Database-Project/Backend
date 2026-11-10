package com.ssu.shop.domain.product.controller;

import com.ssu.shop.domain.product.dto.ProductDtos.ProductCreateRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductDetailResponse;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductUpdateRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.SaleStatusRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller - Product", description = "판매자 상품 등록 / 수정 / 삭제 / 목록")
@RestController
@RequestMapping("/api/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductService productService;

    @Operation(summary = "상품 등록 → 승인 대기(PENDING)",
            description = "본인의 승인된 브랜드에만. 소분류 카테고리, 색상·사이즈 옵션과 옵션별 초기 재고를 함께 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDetailResponse> create(@AuthenticationPrincipal LoginUser loginUser,
                                                     @Valid @RequestBody ProductCreateRequest request) {
        return ApiResponse.success(productService.create(loginUser.id(), request));
    }

    @Operation(summary = "상품 정보 수정 → 재승인 대기",
            description = "null 필드는 변경 안 함. 하나라도 바뀌면 PENDING 으로 돌아가 승인 전까지 숨겨짐. "
                    + "재고 변경은 입고/조정 API 로(승인 불필요)")
    @PatchMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> update(@AuthenticationPrincipal LoginUser loginUser,
                                                     @PathVariable Long productId,
                                                     @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.success(productService.update(loginUser.id(), productId, request));
    }

    @Operation(summary = "판매 상태 변경 (ON_SALE ↔ SOLD_OUT, 승인된 상품만)")
    @PatchMapping("/{productId}/sale-status")
    public ApiResponse<ProductDetailResponse> changeSaleStatus(@AuthenticationPrincipal LoginUser loginUser,
                                                               @PathVariable Long productId,
                                                               @Valid @RequestBody SaleStatusRequest request) {
        return ApiResponse.success(productService.changeSaleStatus(loginUser.id(), productId, request.status()));
    }

    @Operation(summary = "상품 삭제 (승인 불필요)", description = "주문 기록 보존을 위해 DELETED 상태로 바꾼다")
    @DeleteMapping("/{productId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long productId) {
        productService.delete(loginUser.id(), productId);
        return ApiResponse.ok();
    }

    @Operation(summary = "내 상품 목록 (삭제 제외 모든 상태)")
    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> myProducts(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(productService.myProducts(loginUser.id(), status, page, size));
    }

    @Operation(summary = "내 상품 상세 (승인 대기·거절 사유 포함)")
    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> myProduct(@AuthenticationPrincipal LoginUser loginUser,
                                                        @PathVariable Long productId) {
        return ApiResponse.success(productService.getMyProduct(loginUser.id(), productId));
    }
}
