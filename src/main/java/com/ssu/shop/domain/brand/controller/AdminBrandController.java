package com.ssu.shop.domain.brand.controller;

import com.ssu.shop.domain.brand.dto.BrandDtos.AdminBrandResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandResponse;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandStatusChangeRequest;
import com.ssu.shop.domain.brand.service.BrandService;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Brand", description = "브랜드 승인/거절")
@RestController
@RequestMapping("/api/admin/brands")
@RequiredArgsConstructor
public class AdminBrandController {

    private final BrandService brandService;

    @Operation(summary = "브랜드 신청 목록")
    @GetMapping
    public ApiResponse<PageResponse<AdminBrandResponse>> list(
            @RequestParam(required = false) BrandStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(brandService.search(status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))));
    }

    @Operation(summary = "브랜드 승인/거절")
    @PatchMapping("/{brandId}/status")
    public ApiResponse<BrandResponse> changeStatus(@PathVariable Long brandId,
                                                   @Valid @RequestBody BrandStatusChangeRequest request) {
        return ApiResponse.success(brandService.changeStatus(brandId, request));
    }
}
