package com.ssu.shop.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssu.shop.domain.order.dto.OrderDtos.PaymentRequest;
import com.ssu.shop.domain.payment.entity.PaymentMethod;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class PaymentRequestValidationTest {

    final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(PaymentMethod method, String cardNumber) {
        return validator.validate(new PaymentRequest(method, cardNumber)).isEmpty();
    }

    @Test
    void 카드는_16자리가_필요하고_하이픈과_공백을_허용한다() {
        assertThat(valid(PaymentMethod.CARD, "1234567890123456")).isTrue();
        assertThat(valid(PaymentMethod.CARD, "1234-5678-9012-3456")).isTrue();
        assertThat(valid(PaymentMethod.CARD, "1234 5678 9012 3456")).isTrue();
        assertThat(valid(PaymentMethod.CARD, null)).isFalse();
        assertThat(valid(PaymentMethod.CARD, "1234-5678-9012-345")).isFalse();
        assertThat(valid(PaymentMethod.CARD, "abcd-5678-9012-3456")).isFalse();
    }

    @Test
    void 계좌이체와_간편결제는_카드번호를_보내지_않는다() {
        assertThat(valid(PaymentMethod.BANK_TRANSFER, null)).isTrue();
        assertThat(valid(PaymentMethod.EASY_PAY, "")).isTrue();
        assertThat(valid(PaymentMethod.EASY_PAY, "1234567890123456")).isFalse();
        assertThat(valid(null, null)).isFalse();
    }

    @Test
    void 카드번호는_숫자만_남기고_다른_수단은_null() {
        assertThat(new PaymentRequest(PaymentMethod.CARD, "1234-5678-9012-3456").cardDigits()).isEqualTo("1234567890123456");
        assertThat(new PaymentRequest(PaymentMethod.BANK_TRANSFER, null).cardDigits()).isNull();
    }
}
