package com.ssu.shop.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssu.shop.domain.payment.entity.PaymentMethod;
import com.ssu.shop.domain.payment.gateway.MockPaymentGateway;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class MockPaymentGatewayTest {

    final MockPaymentGateway gateway =
            new MockPaymentGateway(Clock.fixed(Instant.parse("2026-11-19T01:00:00Z"), ZoneId.of("Asia/Seoul")));

    @Test
    void 모든_수단을_즉시_승인하고_날짜가_든_승인번호를_준다() {
        assertThat(gateway.approve(PaymentMethod.CARD, "1234567890123456", 50_000)).matches("MOCK-20261119-[0-9A-F]{8}");
        assertThat(gateway.approve(PaymentMethod.BANK_TRANSFER, null, 50_000)).startsWith("MOCK-");
        assertThat(gateway.approve(PaymentMethod.EASY_PAY, null, 50_000)).startsWith("MOCK-");
    }

    @Test
    void 카드번호가_0000으로_끝나면_거절() {
        assertThatThrownBy(() -> gateway.approve(PaymentMethod.CARD, "1234567890120000", 50_000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("카드사")
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_DECLINED);
    }

    @Test
    void 백만원까지는_승인하고_넘으면_모든_수단에서_한도_초과() {
        assertThat(gateway.approve(PaymentMethod.EASY_PAY, null, 1_000_000)).startsWith("MOCK-");
        for (PaymentMethod method : PaymentMethod.values()) {
            String card = method == PaymentMethod.CARD ? "1234567890123456" : null;
            assertThatThrownBy(() -> gateway.approve(method, card, 1_000_001))
                    .hasMessageContaining("한도")
                    .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_DECLINED);
        }
    }
}
