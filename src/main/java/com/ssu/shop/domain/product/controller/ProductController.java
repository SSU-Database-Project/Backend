package com.ssu.shop.domain.product.controller;

import com.ssu.shop.domain.product.dto.ProductDtos.ProductDetailResponse;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.ssu.shop.domain.product.service.ProductService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Product", description = "상품 목록 / 상세 (로그인 필요, 승인된 상품만)")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 목록",
            description = "대분류 categoryId 를 주면 하위 소분류 상품까지 포함. keyword 는 상품명·브랜드명·품번 검색. "
                    + "기본 정렬은 먼저 등록한 순(oldest)")
    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @Parameter(description = "oldest | latest | priceAsc | priceDesc")
            @RequestParam(defaultValue = "oldest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(productService.searchPublic(categoryId, brandId, keyword, sort, page, size));
    }

    @Operation(summary = "상품 상세", description = "품번, 성별, 시즌, 카테고리, 출고 예정, 재고, 색상·사이즈 옵션")
    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> detail(@PathVariable Long productId) {
        return ApiResponse.success(productService.getPublicDetail(productId));
    }
}
