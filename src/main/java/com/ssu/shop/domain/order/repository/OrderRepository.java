package com.ssu.shop.domain.order.repository;

import com.ssu.shop.domain.order.entity.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 내 주문 목록, 최신순 (idx_orders_user_created). 같은 시각이면 나중에 만든 주문이 먼저 */
    Page<Order> findByUser_IdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    /** 내 주문만 찾는다 (남의 주문 id 를 넣으면 없는 것으로 나온다) */
    Optional<Order> findByIdAndUser_Id(Long id, Long userId);
}
