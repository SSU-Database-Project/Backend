package com.readmate.domain.brand.controller;

import com.readmate.domain.brand.dto.BrandDtos.BrandCreateRequest;
import com.readmate.domain.brand.dto.BrandDtos.BrandDetailResponse;
import com.readmate.domain.brand.dto.BrandDtos.BrandResponse;
import com.readmate.domain.brand.service.BrandService;
import com.readmate.global.common.ApiResponse;
import com.readmate.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
