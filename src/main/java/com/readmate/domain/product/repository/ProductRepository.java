package com.readmate.domain.product.repository;

import com.readmate.domain.product.entity.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * 동적 조건 목록 조회.
     * brand, category 는 모두 To-One 이므로 fetch join(EntityGraph) + 페이징을 함께 써도 안전하다.
     * brand 는 NULL 가능 → EntityGraph 는 LEFT OUTER JOIN 으로 가져온다(브랜드 없는 상품도 포함).
     * count 쿼리에는 EntityGraph 가 적용되지 않아 조인 없이 가볍게 실행된다.
     */
    @Override
    @EntityGraph(attributePaths = {"brand", "category"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "category", "seller"})
    Optional<Product> findDetailById(Long id);

    boolean existsByCategory_Id(Long categoryId);
}
