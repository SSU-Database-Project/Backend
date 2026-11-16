package com.ssu.shop.domain.order.repository;

import com.ssu.shop.domain.order.entity.OrderItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * 여러 주문의 주문 줄을 쿼리 1번으로: 줄 + 배송 묶음 + 판매자 + 옵션 + 상품.
     * 주문 목록에서 주문마다 따로 조회하지 않기 위한 것 (주문이 몇 건이든 이 쿼리는 1번).
     */
    @Query("""
            select i from OrderItem i
              join fetch i.shipment s
              join fetch s.seller
              join fetch i.option o
              join fetch o.product
             where s.order.id in :orderIds
             order by s.id, i.id
            """)
    List<OrderItem> findAllWithDetailsByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    /** 여러 배송 묶음의 주문 줄을 쿼리 1번으로 (판매자 배송 목록용) */
    @Query("""
            select i from OrderItem i
              join fetch i.option o
              join fetch o.product
             where i.shipment.id in :shipmentIds
             order by i.id
            """)
    List<OrderItem> findAllWithProductByShipmentIds(@Param("shipmentIds") Collection<Long> shipmentIds);

    /** 한 배송 묶음의 모든 주문 줄 (취소할 때 재고 복원 대상) */
    List<OrderItem> findAllByShipment_Id(Long shipmentId);
}
