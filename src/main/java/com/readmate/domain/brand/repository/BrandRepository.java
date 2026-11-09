package com.readmate.domain.brand.repository;

import com.readmate.domain.brand.entity.Brand;
import com.readmate.domain.seller.entity.SellerStatus;
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
    Page<Brand> searchWithSeller(@Param("status") SellerStatus status, Pageable pageable);

    /** SellerRepository.transitStatus 와 같은 조건부 UPDATE 패턴 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Brand b set b.status = :to where b.id = :id and b.status = :from")
    int transitStatus(@Param("id") Long id, @Param("from") SellerStatus from, @Param("to") SellerStatus to);
}
