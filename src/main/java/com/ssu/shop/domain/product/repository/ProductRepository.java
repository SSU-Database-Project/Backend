package com.ssu.shop.domain.product.repository;

import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * 동적 조건 목록 조회. brand, category 는 모두 To-One 이므로 fetch join(EntityGraph) + 페이징을 함께 써도 안전하다.
     * count 쿼리에는 EntityGraph 가 적용되지 않아 가볍게 실행된다.
     */
    @Override
    @EntityGraph(attributePaths = {"brand", "category"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "brand.seller", "category"})
    Optional<Product> findDetailById(Long id);

    boolean existsByBrand_IdAndProductCode(Long brandId, String productCode);

    /**
     * 관리자 상태 전이(승인·거절·차단·해제)를 조건부 UPDATE 로. WHERE status IN :from 이라 동시에 처리해도 한 요청만 성공한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Product p set p.status = :to, p.rejectReason = :rejectReason
             where p.id = :id and p.status in :from
            """)
    int transitStatus(@Param("id") Long id, @Param("from") Collection<ProductStatus> from,
                      @Param("to") ProductStatus to, @Param("rejectReason") String rejectReason);

    /** 판매자의 아직 삭제되지 않은 상품 id, 오름차순 (판매자 탈퇴 시 잠그는 순서) */
    @Query("""
            select p.id from Product p
             where p.brand.seller.id = :sellerId
               and p.status <> com.ssu.shop.domain.product.entity.ProductStatus.DELETED
             order by p.id
            """)
    List<Long> findActiveIdsBySeller(@Param("sellerId") Long sellerId);

    /** 브랜드의 아직 삭제되지 않은 상품 id, 오름차순 (브랜드 삭제 시 잠그는 순서 = 주문과 같은 순서) */
    @Query("""
            select p.id from Product p
             where p.brand.id = :brandId
               and p.status <> com.ssu.shop.domain.product.entity.ProductStatus.DELETED
             order by p.id
            """)
    List<Long> findActiveIdsByBrand(@Param("brandId") Long brandId);

    /**
     * 상품 하나를 판매 종료(DELETED). PK 로 한 행만 찾으므로 그 행 하나만 잠근다.
     * clearAutomatically 를 쓰지 않는다: 영속성 컨텍스트를 비우면 같은 트랜잭션에서 읽어 둔 엔티티(예: 탈퇴시킬 회원)가
     * 관리 대상에서 빠져, 그 뒤의 변경이 저장되지 않는다.
     */
    @Modifying
    @Query("""
            update Product p set p.status = com.ssu.shop.domain.product.entity.ProductStatus.DELETED
             where p.id = :id and p.status <> com.ssu.shop.domain.product.entity.ProductStatus.DELETED
            """)
    int markDeleted(@Param("id") Long id);
}
