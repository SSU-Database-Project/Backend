package com.ssu.shop.domain.category.dto;

import com.ssu.shop.domain.category.entity.Category;
import com.ssu.shop.domain.product.entity.SizeGroup;
import java.util.List;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    /** 트리 응답: 대분류 → children(소분류). sizeGroup 으로 상품 등록 화면의 사이즈 목록을 고른다. */
    public record CategoryResponse(Long id, Long parentId, String name, SizeGroup sizeGroup, int displayOrder,
                                   List<CategoryResponse> children) {

        public static CategoryResponse of(Category c, List<CategoryResponse> children) {
            return new CategoryResponse(c.getId(), c.getParentId(), c.getName(), c.getSizeGroup(),
                    c.getDisplayOrder(), children);
        }

        public static CategoryResponse leaf(Category c) {
            return of(c, List.of());
        }
    }
}
