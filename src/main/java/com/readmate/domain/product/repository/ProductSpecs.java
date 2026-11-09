package com.readmate.domain.product.repository;

import com.readmate.domain.product.entity.Product;
import com.readmate.domain.product.entity.ProductStatus;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

/** 상품 목록 동적 조건. root.get("brand").get("id") 는 조인 없이 FK 컬럼(brand_id)으로 비교된다. */
public final class ProductSpecs {

    private ProductSpecs() {
    }

    public static Specification<Product> statusIn(Collection<ProductStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Product> categoryIn(Collection<Long> categoryIds) {
        return (root, query, cb) -> root.get("category").get("id").in(categoryIds);
    }

    public static Specification<Product> brandEq(Long brandId) {
        return (root, query, cb) -> cb.equal(root.get("brand").get("id"), brandId);
    }

    public static Specification<Product> sellerEq(Long sellerId) {
        return (root, query, cb) -> cb.equal(root.get("seller").get("id"), sellerId);
    }

    /**
     * LIKE '%키워드%' — 앞 와일드카드 때문에 B-Tree 인덱스를 쓸 수 없다(풀스캔). 시연 규모에서는 허용,
     * 데이터가 커지면 ngram FULLTEXT 인덱스로 전환(계획서 4.8). 사용자가 입력한 % _ 는 이스케이프한다.
     */
    public static Specification<Product> nameContains(String keyword) {
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return (root, query, cb) -> cb.like(root.get("name"), "%" + escaped + "%", '\\');
    }
}
