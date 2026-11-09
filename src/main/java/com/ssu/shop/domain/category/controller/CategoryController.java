package com.ssu.shop.domain.category.controller;

import com.ssu.shop.domain.category.dto.CategoryDtos.CategoryCreateRequest;
import com.ssu.shop.domain.category.dto.CategoryDtos.CategoryResponse;
import com.ssu.shop.domain.category.dto.CategoryDtos.CategoryUpdateRequest;
import com.ssu.shop.domain.category.service.CategoryService;
import com.ssu.shop.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Category", description = "카테고리 조회 / 관리자 관리")
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "카테고리 트리 (대분류 → 소분류)")
    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryResponse>> tree() {
        return ApiResponse.success(categoryService.tree());
    }

    @Operation(summary = "[관리자] 카테고리 추가", description = "parentId 생략 시 대분류. 최대 2단계")
    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        return ApiResponse.success(categoryService.create(request));
    }

    @Operation(summary = "[관리자] 카테고리 수정 (이름 / 정렬순서)")
    @PatchMapping("/api/admin/categories/{categoryId}")
    public ApiResponse<CategoryResponse> update(@PathVariable Long categoryId,
                                                @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.success(categoryService.update(categoryId, request));
    }

    @Operation(summary = "[관리자] 카테고리 삭제", description = "하위 카테고리나 상품이 있으면 409 CATEGORY_IN_USE")
    @DeleteMapping("/api/admin/categories/{categoryId}")
    public ApiResponse<Void> delete(@PathVariable Long categoryId) {
        categoryService.delete(categoryId);
        return ApiResponse.ok();
    }
}
