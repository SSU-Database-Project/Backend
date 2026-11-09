package com.ssu.shop.domain.category.dto;

import com.ssu.shop.domain.category.entity.Category;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    /** parentId 생략 = 대분류 생성 */
    public record CategoryCreateRequest(
            Long parentId,
            @NotBlank @Size(max = 50) String name,
            @Min(0) Integer displayOrder) {
    }

    public record CategoryUpdateRequest(
            @Size(min = 1, max = 50) String name,
            @Min(0) Integer displayOrder) {
    }

    /** 트리 응답: 대분류 → children(소분류) */
    public record CategoryResponse(Long id, Long parentId, String name, int displayOrder,
                                   List<CategoryResponse> children) {

        public static CategoryResponse of(Category c, List<CategoryResponse> children) {
            return new CategoryResponse(c.getId(), c.getParentId(), c.getName(), c.getDisplayOrder(), children);
        }

        public static CategoryResponse leaf(Category c) {
            return of(c, List.of());
        }
    }
}
