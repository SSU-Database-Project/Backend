package com.ssu.shop.domain.brand.repository;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    boolean existsByName(String name);

    List<Brand> findBySeller_IdOrderByIdDesc(Long sellerId);

    @Query(value = """
            select b from Brand b join fetch b.seller
            where (:status is null or b.status = :status)
            """,
            countQuery = "select count(b) from Brand b where (:status is null or b.status = :status)")
    Page<Brand> searchWithSeller(@Param("status") BrandStatus status, Pageable pageable);

    /**
     * 조건부 UPDATE 로 상태 전이. WHERE status = :from 덕분에 동시에 처리해도 한 요청만 영향 행 1을 받는다.
     * updated_at 은 DB 의 ON UPDATE 가 갱신한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Brand b set b.status = :to, b.rejectReason = :rejectReason
             where b.id = :id and b.status = :from
            """)
    int transitStatus(@Param("id") Long id, @Param("from") BrandStatus from, @Param("to") BrandStatus to,
                      @Param("rejectReason") String rejectReason);
}
