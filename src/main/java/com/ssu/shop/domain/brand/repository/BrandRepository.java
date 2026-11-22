package com.ssu.shop.domain.brand.repository;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    boolean existsByName(String name);

    /** 판매자 센터 내 브랜드 목록 (삭제한 브랜드 제외) */
    List<Brand> findBySeller_IdAndStatusNotOrderByIdDesc(Long sellerId, BrandStatus status);

    /** 삭제 전에 브랜드 행을 잠근다 (FOR UPDATE). 같은 브랜드에 상품을 등록하는 요청은 FOR SHARE 로 기다린다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Brand b where b.id = :id")
    Optional<Brand> findByIdForUpdate(@Param("id") Long id);

    /** 상품 등록 때 브랜드 상태를 잠금 읽기 (FOR SHARE) → 동시에 삭제 중이면 끝날 때까지 기다렸다가 DELETED 를 본다 */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select b from Brand b where b.id = :id")
    Optional<Brand> findByIdForShare(@Param("id") Long id);

    @Query(value = """
            select b from Brand b join fetch b.seller
            where (:status is null and b.status <> com.ssu.shop.domain.brand.entity.BrandStatus.DELETED)
               or b.status = :status
            """,
            countQuery = """
            select count(b) from Brand b
            where (:status is null and b.status <> com.ssu.shop.domain.brand.entity.BrandStatus.DELETED)
               or b.status = :status
            """)
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
