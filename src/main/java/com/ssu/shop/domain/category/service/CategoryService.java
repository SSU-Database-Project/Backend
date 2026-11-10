package com.ssu.shop.domain.category.service;

import com.ssu.shop.domain.category.dto.CategoryDtos.CategoryResponse;
import com.ssu.shop.domain.category.entity.Category;
import com.ssu.shop.domain.category.repository.CategoryRepository;
import com.ssu.shop.global.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리는 마이그레이션 시드로 고정된 데이터라 조회만 한다(회의 결정). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * 카테고리 트리. 부모마다 자식을 조회하면 N+1 이 되므로 전체를 쿼리 1번으로 읽고 메모리에서 조립한다.
     * (카테고리는 수십 건 규모라 전체 조회 비용이 작다)
     */
    public List<CategoryResponse> tree() {
        List<Category> all = categoryRepository.findAllByOrderByDisplayOrderAscIdAsc();
        Map<Long, List<CategoryResponse>> childrenByParent = all.stream()
                .filter(c -> !c.isRoot())
                .collect(Collectors.groupingBy(Category::getParentId,
                        Collectors.mapping(CategoryResponse::leaf, Collectors.toList())));
        return all.stream()
                .filter(Category::isRoot)
                .map(root -> CategoryResponse.of(root, childrenByParent.getOrDefault(root.getId(), List.of())))
                .toList();
    }

    /** 상품 필터용: 대분류면 자신 + 소분류 id 전체, 소분류면 자신만 */
    public List<Long> resolveCategoryIds(Long categoryId) {
        Category category = getCategory(categoryId);
        List<Long> ids = new ArrayList<>();
        ids.add(category.getId());
        if (category.isRoot()) {
            categoryRepository.findChildren(categoryId).forEach(child -> ids.add(child.getId()));
        }
        return ids;
    }

    public Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> BusinessException.notFound("카테고리"));
    }
}
