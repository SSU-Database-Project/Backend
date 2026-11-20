package com.ssu.shop.domain.brand.controller;

import com.ssu.shop.domain.brand.dto.BrandDtos.BrandCreateRequest;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandDetailResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandWithProductRequest;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandWithProductResponse;
import com.ssu.shop.domain.brand.service.BrandService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Brand", description = "브랜드 등록 / 조회")
@RestController
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @Operation(summary = "브랜드 등록 (→ PENDING)")
    @PostMapping("/api/seller/brands")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandResponse> create(@AuthenticationPrincipal LoginUser loginUser,
                                             @Valid @RequestBody BrandCreateRequest request) {
        return ApiResponse.success(brandService.create(loginUser.id(), request));
    }

    @Operation(summary = "브랜드 + 첫 상품 한 번에 등록 (둘 다 → PENDING)",
            description = "본문: {\"brand\": 브랜드 등록과 같음, \"product\": 상품 등록과 같되 brandId 없음}. 한 트랜잭션이라 "
                    + "상품이 실패하면 브랜드도 만들어지지 않는다. 관리자는 브랜드를 승인한 뒤 상품을 따로 승인한다 "
                    + "(브랜드 승인 전에 상품을 승인하면 409 BRAND_NOT_APPROVED)")
    @PostMapping("/api/seller/brands/with-product")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandWithProductResponse> createWithProduct(@AuthenticationPrincipal LoginUser loginUser,
                                                                   @Valid @RequestBody BrandWithProductRequest request) {
        return ApiResponse.success(brandService.createWithProduct(loginUser.id(), request));
    }

    @Operation(summary = "내 브랜드 삭제",
            description = "소속 상품은 모두 판매 종료(DELETED), 브랜드명은 다른 브랜드가 다시 쓸 수 있게 된다. "
                    + "주문 기록은 그대로 남는다. 오류: HAS_IN_PROGRESS_ORDERS(409) 이 브랜드 상품의 진행 중 주문이 있음, "
                    + "BRAND_NOT_OWNED(403), NOT_FOUND(404) 없거나 이미 삭제됨")
    @DeleteMapping("/api/seller/brands/{brandId}")
    public ApiResponse<BrandResponse> delete(@AuthenticationPrincipal LoginUser loginUser,
                                             @PathVariable Long brandId) {
        return ApiResponse.success(brandService.deleteMine(loginUser.id(), brandId));
    }

    @Operation(summary = "내 브랜드 목록")
    @GetMapping("/api/seller/brands")
    public ApiResponse<List<BrandResponse>> myBrands(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(brandService.myBrands(loginUser.id()));
    }

    @Operation(summary = "브랜드 상세 + 소속 상품", description = "승인된 브랜드만 조회 가능")
    @GetMapping("/api/brands/{brandId}")
    public ApiResponse<BrandDetailResponse> detail(@PathVariable Long brandId,
                                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(brandService.publicDetail(brandId, page, size));
    }
}
