package com.ssu.shop.domain.product.repository;

import com.ssu.shop.domain.product.entity.ProductOption;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** 목록 화면: 상품별 총 재고를 한 번에 (product_id IN ... GROUP BY) — N+1 방지 */
    @Query("""
            select o.product.id as productId, sum(o.stockQuantity) as totalStock
            from ProductOption o
            where o.product.id in :productIds
            group by o.product.id
            """)
    List<StockSum> sumStockByProductIds(@Param("productIds") Collection<Long> productIds);

    /**
     * [BE-2] 재고 변경용 잠금: 옵션 행을 id 오름차순으로 잠그고(SELECT ... ORDER BY id FOR UPDATE) 잠근 id 를 돌려준다.
     * 누가 부르든 같은 순서로 잠그므로, 여러 옵션을 한 번에 주문해도 서로를 기다리다 멈추는 데드락이 생기지 않는다.
     * 엔티티가 아니라 id 만 조회한다 — 같은 트랜잭션에서 이미 읽어 둔 엔티티의 옛 값과 섞이지 않게 하려는 것이다.
     * 재고 값은 StockService 가 잠근 뒤에 다시 읽는다. 트랜잭션 안에서만 호출할 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o.id from ProductOption o where o.id in :ids order by o.id")
    List<Long> lockAllByIdInOrder(@Param("ids") Collection<Long> ids);

    interface StockSum {
        Long getProductId();

        Long getTotalStock();
    }
}
