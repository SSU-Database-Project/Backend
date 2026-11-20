-- =====================================================================
-- V8: 판매자 취소 + 브랜드 삭제 (2026-11-20, 팀 결정)
--
-- 1) 판매자 취소: 판매자도 배송 전(주문완료·배송준비)에 취소할 수 있다. 누가 취소했는지와 사유를 남긴다.
--    canceled_by = BUYER | SELLER, 판매자 취소는 사유 필수. 구매자는 사유 선택.
--    V8 이전 취소는 모두 구매자 취소였다 (판매자 취소 기능이 없었음) → BUYER 로 채운다.
-- 2) 브랜드 삭제: 판매자(본인)·관리자가 삭제. 행은 남기고(주문 기록이 상품 → 브랜드를 참조) 상태만 DELETED.
--    삭제한 브랜드의 이름은 'deleted_{id}_원래이름' 으로 바꿔 다른 브랜드가 같은 이름을 다시 쓸 수 있게 한다.
-- =====================================================================

ALTER TABLE shipments
    ADD COLUMN canceled_by   VARCHAR(10)  NULL AFTER canceled_at,
    ADD COLUMN cancel_reason VARCHAR(255) NULL AFTER canceled_by;

UPDATE shipments SET canceled_by = 'BUYER' WHERE status = 'CANCELED';

ALTER TABLE shipments
    ADD CONSTRAINT ck_shipments_canceled_by       CHECK ((status = 'CANCELED') = (canceled_by IS NOT NULL)),
    ADD CONSTRAINT ck_shipments_canceled_by_value CHECK (canceled_by IS NULL OR canceled_by IN ('BUYER', 'SELLER')),
    ADD CONSTRAINT ck_shipments_seller_reason     CHECK (canceled_by IS NULL OR canceled_by <> 'SELLER' OR cancel_reason IS NOT NULL);

ALTER TABLE brands DROP CHECK ck_brands_status;
ALTER TABLE brands
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER reject_reason,
    ADD CONSTRAINT ck_brands_status  CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'DELETED')),
    ADD CONSTRAINT ck_brands_deleted CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL));
