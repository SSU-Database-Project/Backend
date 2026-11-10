package com.ssu.shop.domain.category.controller;

import com.ssu.shop.domain.category.dto.CategoryDtos.CategoryResponse;
import com.ssu.shop.domain.category.service.CategoryService;
import com.ssu.shop.domain.product.dto.ProductDtos.ColorResponse;
import com.ssu.shop.domain.product.dto.ProductDtos.SizeResponse;
import com.ssu.shop.domain.product.entity.SizeGroup;
import com.ssu.shop.domain.product.repository.ColorRepository;
import com.ssu.shop.domain.product.repository.SizeRepository;
import com.ssu.shop.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Catalog", description = "고정 목록: 카테고리 / 색상 / 사이즈")
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final ColorRepository colorRepository;
    private final SizeRepository sizeRepository;

    @Operation(summary = "카테고리 트리 (대분류 → 소분류)")
    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryResponse>> tree() {
        return ApiResponse.success(categoryService.tree());
    }

    @Operation(summary = "색상 목록 (상품 등록 시 선택)")
    @GetMapping("/api/colors")
    public ApiResponse<List<ColorResponse>> colors() {
        return ApiResponse.success(colorRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(ColorResponse::from).toList());
    }

    @Operation(summary = "사이즈 목록", description = "sizeGroup 을 주면 그 체계만 (카테고리의 sizeGroup 과 같은 값)")
    @GetMapping("/api/sizes")
    public ApiResponse<List<SizeResponse>> sizes(@RequestParam(required = false) SizeGroup sizeGroup) {
        var sizes = sizeGroup == null
                ? sizeRepository.findAllByOrderBySizeGroupAscDisplayOrderAsc()
                : sizeRepository.findAllBySizeGroupOrderByDisplayOrderAsc(sizeGroup);
        return ApiResponse.success(sizes.stream().map(SizeResponse::from).toList());
    }
}
