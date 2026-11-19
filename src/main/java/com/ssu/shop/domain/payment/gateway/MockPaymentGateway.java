package com.ssu.shop.domain.payment.gateway;

import com.ssu.shop.domain.payment.entity.PaymentMethod;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 모의 결제사 (팀 결정 2026-11-19). 실제 돈은 오가지 않고 즉시 승인한다.
 * 시연용 거절 규칙 (거절되면 주문·재고·장바구니가 모두 롤백된다):
 *  - 결제 금액이 100만 원을 넘으면 모든 수단에서 "결제 한도 초과"
 *  - 카드번호가 0000 으로 끝나면 "카드사 승인 거절"
 */
@Component
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    public static final long LIMIT = 1_000_000L;
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final Clock clock;

    @Override
    public String approve(PaymentMethod method, String cardDigits, long amount) {
        if (amount > LIMIT) {
            throw new BusinessException(ErrorCode.PAYMENT_DECLINED,
                    "결제 한도(100만 원)를 초과해 승인이 거절되었습니다.");
        }
        if (method == PaymentMethod.CARD && cardDigits.endsWith("0000")) {
            throw new BusinessException(ErrorCode.PAYMENT_DECLINED, "카드사에서 승인을 거절했습니다.");
        }
        // MOCK-20261119-1A2B3C4D (uk_payments_approval_no 가 혹시 모를 중복을 막는다)
        return "MOCK-" + LocalDate.now(clock).format(DAY) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
