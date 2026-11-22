package com.ssu.shop.domain.payment.gateway;

import com.ssu.shop.domain.payment.entity.PaymentMethod;

/**
 * 결제 승인 창구. 지금은 MockPaymentGateway(모의 결제사) 하나뿐이다.
 * 실제 PG(토스페이먼츠 등)를 붙일 때는 이 인터페이스의 구현만 바꾸면 주문 흐름은 그대로 쓸 수 있다.
 */
public interface PaymentGateway {

    /**
     * @param cardDigits 카드 결제일 때 숫자 16자리, 아니면 null
     * @return 승인번호
     * @throws com.ssu.shop.global.error.BusinessException PAYMENT_DECLINED (승인 거절)
     */
    String approve(PaymentMethod method, String cardDigits, long amount);
}
