package com.ssu.shop.domain.order.repository;

import com.ssu.shop.domain.order.entity.CancelActor;
import com.ssu.shop.domain.order.entity.Shipment;
import com.ssu.shop.domain.order.entity.ShipmentStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 상태를 바꾸는 메서드는 모두 "허용된 이전 상태"를 WHERE 에 넣은 조건부 UPDATE 다.
 * 돌려주는 값은 바뀐 행 수 — 1 이면 성공, 0 이면 그 사이 상태가 달라진 것이다.
 * 같은 배송 묶음에 두 요청이 동시에 와도 DB 가 한 줄씩 처리하므로 한 요청만 1 을 받는다.
 */
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    /** 배송 묶음과 주문을 함께 (소유권 확인용) */
    @EntityGraph(attributePaths = "order")
    Optional<Shipment> findWithOrderById(Long id);

    /** 판매자의 배송 묶음 전체 (idx_shipments_seller_created). 주문은 To-One 이라 함께 가져와도 페이징이 안전하다 */
    @EntityGraph(attributePaths = "order")
    Page<Shipment> findBySeller_Id(Long sellerId, Pageable pageable);

    /** 판매자의 배송 묶음을 상태로 걸러서 (V5 idx_shipments_seller_status_created) */
    @EntityGraph(attributePaths = "order")
    Page<Shipment> findBySeller_IdAndStatus(Long sellerId, ShipmentStatus status, Pageable pageable);

    /** 판매자의 배송 상태 변경: from 상태일 때만 to 로 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Shipment s set s.status = :to where s.id = :id and s.status = :from")
    int transit(@Param("id") Long id, @Param("from") ShipmentStatus from, @Param("to") ShipmentStatus to);

    /**
     * 취소: from 상태일 때만. 상태·취소 시각·취소한 사람·사유를 함께 넣는다 (DB CHECK).
     * 구매자는 from = {ORDERED}, 판매자는 {ORDERED, PREPARING} (배송 전까지).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Shipment s set s.status = com.ssu.shop.domain.order.entity.ShipmentStatus.CANCELED, s.canceledAt = :now,
                   s.canceledBy = :by, s.cancelReason = :reason
             where s.id = :id and s.status in :from
            """)
    int cancel(@Param("id") Long id, @Param("from") Collection<ShipmentStatus> from, @Param("now") LocalDateTime now,
               @Param("by") CancelActor by, @Param("reason") String reason);

    /** 구매확정: 배송완료(DELIVERED)일 때만. 상태와 확정 시각을 함께 넣는다 (DB CHECK) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Shipment s set s.status = com.ssu.shop.domain.order.entity.ShipmentStatus.CONFIRMED, s.confirmedAt = :now
             where s.id = :id and s.status = com.ssu.shop.domain.order.entity.ShipmentStatus.DELIVERED
            """)
    int confirm(@Param("id") Long id, @Param("now") LocalDateTime now);

    // ----- 진행 중인 주문 (주문완료·배송준비·배송중) 수: 회원 정지·탈퇴 판단용 (팀 결정) -----
    // 배송완료(DELIVERED)는 진행 중으로 보지 않는다. 구매확정·취소도 끝난 주문이다.
    // *ForUpdate 는 잠금 읽기(FOR SHARE)라 트랜잭션의 스냅숏이 아니라 커밋된 최신 행을 센다.
    // 사용 이유와 잠금 순서는 AccountLifecycle 주석 참고.

    /** 구매자 기준. idx_orders_user_created → uk_shipments_order_seller 로 그 회원의 주문만 읽는다 */
    @Query(value = """
            SELECT COUNT(*) FROM shipments s JOIN orders o ON o.id = s.order_id
             WHERE o.user_id = :userId AND s.status IN ('ORDERED', 'PREPARING', 'SHIPPING')
            """, nativeQuery = true)
    long countInProgressByBuyer(@Param("userId") Long userId);

    @Query(value = """
            SELECT COUNT(*) FROM shipments s JOIN orders o ON o.id = s.order_id
             WHERE o.user_id = :userId AND s.status IN ('ORDERED', 'PREPARING', 'SHIPPING')
               FOR SHARE
            """, nativeQuery = true)
    long countInProgressByBuyerForUpdate(@Param("userId") Long userId);

    /** 판매자 기준. V5 idx_shipments_seller_status_created 로 그 판매자의 진행 중 묶음만 읽는다 */
    @Query(value = """
            SELECT COUNT(*) FROM shipments
             WHERE seller_id = :sellerId AND status IN ('ORDERED', 'PREPARING', 'SHIPPING')
            """, nativeQuery = true)
    long countInProgressBySeller(@Param("sellerId") Long sellerId);

    @Query(value = """
            SELECT COUNT(*) FROM shipments
             WHERE seller_id = :sellerId AND status IN ('ORDERED', 'PREPARING', 'SHIPPING')
               FOR SHARE
            """, nativeQuery = true)
    long countInProgressBySellerForUpdate(@Param("sellerId") Long sellerId);

    /** 브랜드 기준 (V8 브랜드 삭제): 그 브랜드 상품이 담긴 진행 중 배송 묶음 수. 잠금 읽기 */
    @Query(value = """
            SELECT COUNT(DISTINCT sh.id) FROM shipments sh
              JOIN order_items oi     ON oi.shipment_id = sh.id
              JOIN product_options po ON po.id = oi.option_id
              JOIN products p         ON p.id = po.product_id
             WHERE p.brand_id = :brandId AND sh.status IN ('ORDERED', 'PREPARING', 'SHIPPING')
               FOR SHARE
            """, nativeQuery = true)
    long countInProgressByBrandForUpdate(@Param("brandId") Long brandId);
}
