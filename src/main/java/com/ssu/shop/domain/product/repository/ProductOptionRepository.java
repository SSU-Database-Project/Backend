package com.ssu.shop.domain.product.repository;

import com.ssu.shop.domain.product.entity.ProductOption;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** [BE-2] 재고 차감용 락/조건부 UPDATE 메서드는 BE-2 가 추가한다. */
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    /** 상세 화면: 옵션 + 색상 + 사이즈를 쿼리 1번으로 (uk_product_options_combination 의 product_id 선두 사용) */
    @Query("""
            select o from ProductOption o join fetch o.color join fetch o.size
            where o.product.id = :productId
            order by o.color.displayOrder, o.size.displayOrder
            """)
    List<ProductOption> findAllWithColorAndSize(@Param("productId") Long productId);

    /** 판매자·관리자 목록 (V8): 한 페이지 상품들의 옵션 + 색상 + 사이즈를 쿼리 1번으로 (product_id IN ...) — N+1 방지 */
    @Query("""
            select o from ProductOption o join fetch o.color join fetch o.size
            where o.product.id in :productIds
            order by o.product.id, o.color.displayOrder, o.size.displayOrder
            """)
    List<ProductOption> findAllWithColorAndSizeByProductIds(@Param("productIds") Collection<Long> productIds);

    /** 목록 화면: 상품별 총 재고를 한 번에 (product_id IN ... GROUP BY) — N+1 방지 */
    @Query("""
            select o.product.id as productId, sum(o.stockQuantity) as totalStock
            from ProductOption o
            where o.product.id in :productIds
            group by o.product.id
            """)
    List<StockSum> sumStockByProductIds(@Param("productIds") Collection<Long> productIds);

    interface StockSum {
        Long getProductId();

        Long getTotalStock();
    }
}
