-- =====================================================================
-- V7: 모의 결제 (2026-11-19, 팀 결정: 실제 PG 없이 서버가 결제를 승인하고 기록을 남긴다)
--
-- payments        : 주문 1건 = 결제 1건. 주문 생성과 같은 트랜잭션에서 만든다 (승인 거절이면 주문까지 롤백).
-- payment_refunds : 판매자별 배송 묶음 취소 1건 = 부분 환불 1건. 금액 = 그 묶음의 주문 라인 합계.
--
-- 결제 금액은 클라이언트가 보내지 않는다. 서버가 주문 총액(orders.total_price)으로 승인한다 → 화면에서 금액 조작 불가.
-- 카드번호는 저장하지 않고 끝 4자리만 저장한다.
-- 결제 상태(부분 환불·전액 환불)는 저장하지 않고 환불 합계로 계산한다 → 같은 사실을 두 곳에 두지 않는다 (3NF).
-- V7 이전에 만들어진 주문에는 결제가 없을 수 있다 (시연 주문은 R__demo_payments 가 채운다).
-- =====================================================================
CREATE TABLE payments (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    method       VARCHAR(20)  NOT NULL,            -- CARD 카드 / BANK_TRANSFER 계좌이체 / EASY_PAY 간편결제
    amount       BIGINT       NOT NULL,            -- 승인 금액 = 주문 당시 총액
    card_last4   CHAR(4)      NULL,                -- 카드 결제일 때만, 끝 4자리
    approval_no  VARCHAR(30)  NOT NULL,            -- 모의 결제사 승인번호
    paid_at      DATETIME(6)  NOT NULL,            -- 승인 시각 = 주문 시각
    CONSTRAINT pk_payments             PRIMARY KEY (id),
    CONSTRAINT uk_payments_order       UNIQUE (order_id),
    CONSTRAINT uk_payments_approval_no UNIQUE (approval_no),
    CONSTRAINT fk_payments_order       FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payments_method      CHECK (method IN ('CARD', 'BANK_TRANSFER', 'EASY_PAY')),
    CONSTRAINT ck_payments_amount      CHECK (amount >= 0),
    -- 카드 결제 ⇔ 끝 4자리 있음 (숫자 4자리)
    CONSTRAINT ck_payments_card_last4  CHECK ((method = 'CARD') = (card_last4 IS NOT NULL)),
    CONSTRAINT ck_payments_card_digits CHECK (card_last4 IS NULL OR card_last4 REGEXP '^[0-9]{4}$')
) ENGINE = InnoDB;

CREATE TABLE payment_refunds (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    payment_id   BIGINT       NOT NULL,
    shipment_id  BIGINT       NOT NULL,
    amount       BIGINT       NOT NULL,            -- 취소한 배송 묶음의 주문 라인 합계
    refunded_at  DATETIME(6)  NOT NULL,            -- = shipments.canceled_at
    CONSTRAINT pk_payment_refunds          PRIMARY KEY (id),
    -- 배송 묶음 하나는 한 번만 환불된다 (취소를 두 번 눌러도 환불은 1건)
    CONSTRAINT uk_payment_refunds_shipment UNIQUE (shipment_id),
    CONSTRAINT fk_payment_refunds_payment  FOREIGN KEY (payment_id)  REFERENCES payments (id),
    CONSTRAINT fk_payment_refunds_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id),
    CONSTRAINT ck_payment_refunds_amount   CHECK (amount >= 0),
    INDEX idx_payment_refunds_payment (payment_id)          -- 결제별 환불 합계 (FK 인덱스 겸용)
) ENGINE = InnoDB;
