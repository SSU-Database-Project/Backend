package com.ssu.shop.domain.payment.repository;

import com.ssu.shop.domain.payment.entity.Payment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** uk_payments_order */
    Optional<Payment> findByOrder_Id(Long orderId);

    /** 주문 목록 한 페이지의 결제를 한 번에 (uk_payments_order IN 조회) */
    List<Payment> findAllByOrder_IdIn(Collection<Long> orderIds);
}
