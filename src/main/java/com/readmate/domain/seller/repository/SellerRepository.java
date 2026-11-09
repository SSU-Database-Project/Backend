package com.readmate.domain.seller.repository;

import com.readmate.domain.seller.entity.Seller;
import com.readmate.domain.seller.entity.SellerStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellerRepository extends JpaRepository<Seller, Long> {

    /** uk_sellers_user 인덱스 사용 */
    Optional<Seller> findByUserId(Long userId);

    Optional<Seller> findByBusinessNumber(String businessNumber);

    /** 관리자 신청 목록: 신청자 이메일을 함께 보여주므로 user 를 fetch join (N+1 방지) */
    @Query(value = """
            select s from Seller s join fetch s.user
            where (:status is null or s.status = :status)
            """,
            countQuery = "select count(s) from Seller s where (:status is null or s.status = :status)")
    Page<Seller> searchWithUser(@Param("status") SellerStatus status, Pageable pageable);

    /**
     * 조건부 UPDATE 로 상태 전이.
     * WHERE status = :from 조건 덕분에 관리자 두 명이 동시에 승인/거절해도 한 명만 성공한다
     * (영향 행 수 0 = 이미 다른 요청이 처리함). updated_at 은 DB 의 ON UPDATE 가 갱신한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Seller s
               set s.status = :to, s.rejectReason = :rejectReason, s.approvedAt = :approvedAt
             where s.id = :id and s.status = :from
            """)
    int transitStatus(@Param("id") Long id,
                      @Param("from") SellerStatus from,
                      @Param("to") SellerStatus to,
                      @Param("rejectReason") String rejectReason,
                      @Param("approvedAt") LocalDateTime approvedAt);
}
