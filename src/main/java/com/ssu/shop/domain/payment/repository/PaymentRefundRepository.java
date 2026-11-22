package com.ssu.shop.domain.payment.repository;

import com.ssu.shop.domain.payment.entity.PaymentRefund;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    /** 결제들의 환불 내역 (idx_payment_refunds_payment) */
    List<PaymentRefund> findAllByPayment_IdInOrderByIdAsc(Collection<Long> paymentIds);
}
