package com.readmate.domain.category.service;

import com.readmate.domain.category.dto.CategoryDtos.CategoryCreateRequest;
import com.readmate.domain.category.dto.CategoryDtos.CategoryResponse;
import com.readmate.domain.category.dto.CategoryDtos.CategoryUpdateRequest;
import com.readmate.domain.category.entity.Category;
import com.readmate.domain.category.repository.CategoryRepository;
import com.readmate.domain.product.repository.ProductRepository;
import com.readmate.global.error.BusinessException;
import com.readmate.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

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

    // ===== 관리자 =====

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        Category parent = null;
        if (request.parentId() != null) {
            parent = getCategory(request.parentId());
            if (!parent.isRoot()) {
                throw new BusinessException(ErrorCode.CATEGORY_DEPTH_EXCEEDED);
            }
        }
        String name = request.name().trim();
        validateNameUnique(parent == null ? null : parent.getId(), name);

        Category category = Category.create(parent, name,
                request.displayOrder() == null ? 0 : request.displayOrder());
        try {
            categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }
        return CategoryResponse.leaf(category);
    }

    @Transactional
    public CategoryResponse update(Long categoryId, CategoryUpdateRequest request) {
        Category category = getCategory(categoryId);
        String name = request.name() == null ? null : request.name().trim();
        if (name != null && !name.equals(category.getName())) {
            validateNameUnique(category.getParentId(), name);
        }
        category.update(name, request.displayOrder());
        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }
        return CategoryResponse.leaf(category);
    }

    /**
     * 하위 카테고리나 상품이 있으면 삭제 불가.
     * 사전 확인은 사용자 메시지용이고, 동시에 상품이 등록되는 경우는 FK(RESTRICT)가 최종적으로 막는다.
     */
    @Transactional
    public void delete(Long categoryId) {
        Category category = getCategory(categoryId);
        if (categoryRepository.hasChildren(categoryId) || productRepository.existsByCategory_Id(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
        }
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE);
        }
    }

    /**
     * UNIQUE(parent_id, name) 은 parent_id 가 NULL 인 대분류끼리의 중복을 막지 못한다(MySQL 에서 NULL ≠ NULL).
     * 그래서 대분류는 서비스에서 확인한다. 소분류는 DB 제약도 함께 막는다.
     */
    private void validateNameUnique(Long parentId, String name) {
        boolean duplicated = Objects.isNull(parentId)
                ? categoryRepository.existsByParentIsNullAndName(name)
                : categoryRepository.existsByParent_IdAndName(parentId, name);
        if (duplicated) {
            throw new BusinessException(ErrorCode.DUPLICATE_CATEGORY_NAME);
        }
    }
}
