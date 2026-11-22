-- =====================================================================
-- 시연용 SQL (발표 중 데이터 확인 · 상태 변경 · 예외 상황 · 권한 검증)
--
-- 실행: MySQL Workbench 또는 mysql 클라이언트에서 블록 하나씩 (블록 = "-- [번호]" 부터 다음 블록 전까지)
--   Docker:  mysql -h 127.0.0.1 -P 3308 -u shop -pshop1234 --default-character-set=utf8mb4 shop
--   서버:    docker exec -it shop-mysql sh -c 'mysql -ushop -p"$MYSQL_PASSWORD" --default-character-set=utf8mb4 shop'
-- 계정: 시연 데이터(local 프로필) 기준 — 구매자 buyer1@demo.com, 판매자 store.a@demo.com, 관리자 admin@shop.com
--
-- 구성
--   [A] 현황 보기 (읽기만)            [B] 주문 1건 추적 (읽기만)
--   [C] 예외 상황: DB 제약이 막는 것 — 모두 트랜잭션 안에서 실행하고 ROLLBACK (데이터 안 바뀜)
--   [D] 권한·상태 변경 (실제로 바꿈, 되돌리는 문장 포함)
--   [E] 동시성: 세션 2개로 잠금 보여 주기
--   [F] 통계 · 실행 계획 · 정합성 검사
-- =====================================================================

-- 시연할 대상 (필요하면 바꾼다)
SET @buyer  = (SELECT id FROM users WHERE email = 'buyer1@demo.com');
SET @seller_user = (SELECT id FROM users WHERE email = 'store.a@demo.com');
SET @seller = (SELECT id FROM sellers WHERE user_id = @seller_user);


-- =====================================================================
-- [A] 현황 보기
-- =====================================================================

-- [A1] 테이블 17개와 행 수 (Flyway 마이그레이션 V1~V8)
SELECT table_name, table_rows
  FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
 ORDER BY table_name;
SELECT version, description, installed_on FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank;

-- [A2] 회원: 역할 × 상태
SELECT role, status, COUNT(*) AS cnt FROM users GROUP BY role, status ORDER BY role, status;

-- [A3] 배송 묶음 상태별 건수 (판매자별 배송: 주문완료 → 배송준비 → 배송중 → 배송완료 → 구매확정 / 취소)
SELECT status, COUNT(*) AS cnt FROM shipments GROUP BY status
 ORDER BY FIELD(status, 'ORDERED', 'PREPARING', 'SHIPPING', 'DELIVERED', 'CONFIRMED', 'CANCELED');

-- [A4] 결제수단별 결제 · 환불 합계 (모의 결제, V7)
SELECT p.method,
       COUNT(*)                         AS payments,
       SUM(p.amount)                    AS paid,
       COALESCE(SUM(r.refunded), 0)     AS refunded,
       SUM(p.amount) - COALESCE(SUM(r.refunded), 0) AS balance
  FROM payments p
  LEFT JOIN (SELECT payment_id, SUM(amount) AS refunded FROM payment_refunds GROUP BY payment_id) r
         ON r.payment_id = p.id
 GROUP BY p.method;

-- [A5] 취소: 누가 취소했나 (V8: 구매자 / 판매자, 판매자는 사유 필수)
SELECT canceled_by, COUNT(*) AS cnt, MAX(cancel_reason) AS sample_reason
  FROM shipments WHERE status = 'CANCELED' GROUP BY canceled_by;

-- [A6] 판매자 A의 처리할 배송 (V5 인덱스 shipments(seller_id, status, created_at) 사용)
SELECT sh.id AS shipment_id, sh.order_id, sh.status, sh.created_at
  FROM shipments sh
 WHERE sh.seller_id = @seller AND sh.status IN ('ORDERED', 'PREPARING', 'SHIPPING')
 ORDER BY sh.created_at DESC;

-- [A7] 판매자 A의 옵션별 재고와 마지막 재고 이력 (재고 = 마지막 이력의 quantity_after)
SELECT p.name, c.name AS color, s.label AS size, o.stock_quantity,
       (SELECT h.quantity_after FROM stock_history h WHERE h.option_id = o.id ORDER BY h.id DESC LIMIT 1) AS last_history
  FROM product_options o
  JOIN products p ON p.id = o.product_id
  JOIN brands b   ON b.id = p.brand_id
  JOIN colors c   ON c.id = o.color_id
  JOIN sizes s    ON s.id = o.size_id
 WHERE b.seller_id = @seller
 ORDER BY p.id, c.display_order, s.display_order
 LIMIT 20;


-- =====================================================================
-- [B] 주문 1건 추적: 주문 → 판매자별 배송 묶음 → 주문 라인 → 결제 → 환불 → 재고 이력
--     화면에서 주문·취소를 한 직후 실행하면 DB 에 무엇이 생겼는지 보인다
-- =====================================================================

-- [B1] 구매자의 가장 최근 주문
SET @order = (SELECT id FROM orders WHERE user_id = @buyer ORDER BY created_at DESC, id DESC LIMIT 1);
SELECT id, total_price, receiver_name, zip_code, created_at FROM orders WHERE id = @order;

-- [B2] 판매자별 배송 묶음 (취소자·사유 포함)
SELECT sh.id AS shipment_id, s.company_name AS seller, sh.status, sh.canceled_by, sh.cancel_reason,
       sh.canceled_at, sh.confirmed_at
  FROM shipments sh JOIN sellers s ON s.id = sh.seller_id
 WHERE sh.order_id = @order;

-- [B3] 주문 라인: 주문 당시 상품명·가격 스냅샷 vs 지금 상품 가격
SELECT oi.shipment_id, oi.product_name, oi.color_name, oi.size_label, oi.unit_price AS price_at_order,
       p.price AS price_now, oi.quantity, oi.unit_price * oi.quantity AS line_total
  FROM order_items oi
  JOIN shipments sh       ON sh.id = oi.shipment_id
  JOIN product_options po ON po.id = oi.option_id
  JOIN products p         ON p.id = po.product_id
 WHERE sh.order_id = @order;

-- [B4] 결제 (카드는 끝 4자리만 저장) + 환불 내역
SELECT id, method, amount, card_last4, approval_no, paid_at FROM payments WHERE order_id = @order;
SELECT r.shipment_id, r.amount, r.refunded_at
  FROM payment_refunds r JOIN payments p ON p.id = r.payment_id
 WHERE p.order_id = @order;

-- [B5] 이 주문의 재고 이력 (주문 → ORDER, 취소 → CANCEL)
SELECT h.option_id, h.type, h.change_qty, h.quantity_after, h.created_at
  FROM stock_history h
  JOIN order_items oi ON oi.id = h.order_item_id
  JOIN shipments sh   ON sh.id = oi.shipment_id
 WHERE sh.order_id = @order
 ORDER BY h.id;


-- =====================================================================
-- [C] 예외 상황: 잘못된 데이터는 DB 가 거부한다 (CHECK · UNIQUE · FK)
--     각 블록은 트랜잭션 안에서 실행하고 ROLLBACK 한다. 에러 메시지에 제약 이름이 보인다
-- =====================================================================

-- [C1] 재고는 음수가 될 수 없다 → ck_product_options_stock
START TRANSACTION;
UPDATE product_options SET stock_quantity = -1
 WHERE id = (SELECT id FROM (SELECT id FROM product_options LIMIT 1) x);
ROLLBACK;

-- [C2] 같은 상품에 같은 색상·사이즈 옵션은 하나 → uk_product_options_combination
START TRANSACTION;
INSERT INTO product_options (product_id, color_id, size_id, stock_quantity)
SELECT product_id, color_id, size_id, 1 FROM product_options LIMIT 1;
ROLLBACK;

-- [C3] 같은 상품에 좋아요 두 번 → PRIMARY (복합 PK)
START TRANSACTION;
INSERT INTO likes (user_id, product_id) SELECT user_id, product_id FROM likes LIMIT 1;
ROLLBACK;

-- [C4] 취소 상태인데 취소 시각·취소자가 없다 → ck_shipments_canceled_at / ck_shipments_canceled_by
START TRANSACTION;
UPDATE shipments SET status = 'CANCELED'
 WHERE id = (SELECT id FROM (SELECT id FROM shipments WHERE status = 'ORDERED' LIMIT 1) x);
ROLLBACK;

-- [C5] 판매자 취소는 사유가 필수 → ck_shipments_seller_reason (V8)
START TRANSACTION;
UPDATE shipments SET status = 'CANCELED', canceled_at = NOW(6), canceled_by = 'SELLER', cancel_reason = NULL
 WHERE id = (SELECT id FROM (SELECT id FROM shipments WHERE status = 'ORDERED' LIMIT 1) x);
ROLLBACK;

-- [C6] 같은 배송 묶음을 두 번 환불할 수 없다 → uk_payment_refunds_shipment (V7)
START TRANSACTION;
INSERT INTO payment_refunds (payment_id, shipment_id, amount, refunded_at)
SELECT payment_id, shipment_id, amount, NOW(6) FROM payment_refunds LIMIT 1;
ROLLBACK;

-- [C7] 카드 결제는 끝 4자리 숫자만, 카드가 아니면 비어 있어야 한다 → ck_payments_card_digits / ck_payments_card_last4
START TRANSACTION;
UPDATE payments SET card_last4 = '12ab'
 WHERE id = (SELECT id FROM (SELECT id FROM payments WHERE method = 'CARD' LIMIT 1) x);
ROLLBACK;
START TRANSACTION;
UPDATE payments SET card_last4 = '1234'
 WHERE id = (SELECT id FROM (SELECT id FROM payments WHERE method <> 'CARD' LIMIT 1) x);
ROLLBACK;

-- [C8] 사업자번호는 숫자 10자리, 중복 불가 → ck_sellers_business_number / uk_sellers_business_number
START TRANSACTION;
UPDATE sellers SET business_number = '12345' WHERE id = @seller;
ROLLBACK;

-- [C9] 시즌 형식(24SS·24FW), 출고일 0~30 → ck_products_season / ck_products_ship_days
START TRANSACTION;
UPDATE products SET season = '2024' WHERE id = (SELECT id FROM (SELECT id FROM products LIMIT 1) x);
ROLLBACK;
START TRANSACTION;
UPDATE products SET ship_days = 99 WHERE id = (SELECT id FROM (SELECT id FROM products LIMIT 1) x);
ROLLBACK;

-- [C10] 탈퇴 상태 ⇔ 탈퇴 시각, 브랜드 삭제 ⇔ 삭제 시각 → ck_users_withdrawn / ck_brands_deleted (V8)
START TRANSACTION;
UPDATE users SET status = 'WITHDRAWN' WHERE id = @buyer;
ROLLBACK;
START TRANSACTION;
UPDATE brands SET status = 'DELETED' WHERE seller_id = @seller LIMIT 1;
ROLLBACK;

-- [C11] 주문이 참조하는 회원·옵션은 지울 수 없다 → FK (탈퇴·삭제는 행을 남기고 상태로 처리)
START TRANSACTION;
DELETE FROM users WHERE id = @buyer;
ROLLBACK;


-- =====================================================================
-- [D] 권한 · 상태 변경 (실제로 바꾼다 → 화면에서 결과 확인 → 되돌리기 문장 실행)
-- =====================================================================

-- [D1] 역할별 접근: 토큰의 역할이 아니라 DB 의 최신 role 로 매 요청 판단한다
SELECT u.email, u.role, s.company_name, COUNT(b.id) AS brands
  FROM users u
  LEFT JOIN sellers s ON s.user_id = u.id
  LEFT JOIN brands b  ON b.seller_id = s.id AND b.status <> 'DELETED'
 WHERE u.email IN ('admin@shop.com', 'store.a@demo.com', 'buyer1@demo.com')
 GROUP BY u.id, u.email, u.role, s.company_name;

-- [D2] 회원 정지 → 이미 로그인한 화면도 다음 요청부터 401 (JWT 필터가 매 요청 status 확인)
--      주의: 이 SQL 은 API 의 "진행 중 주문이 있으면 정지 불가" 규칙을 거치지 않는다. 규칙 시연은 관리자 화면에서 한다
UPDATE users SET status = 'SUSPENDED' WHERE email = 'buyer2@demo.com';
-- 되돌리기
UPDATE users SET status = 'ACTIVE' WHERE email = 'buyer2@demo.com';

-- [D3] 동시 로그인 방지: 로그인할 때마다 바뀌는 식별값 (V6). 다른 브라우저에서 로그인 전후로 비교
SELECT email, login_session_id FROM users WHERE email = 'buyer1@demo.com';

-- [D4] 관리자 계정 추가: 가입으로는 관리자가 될 수 없다. 일반회원으로 가입한 뒤 DB 에서만 바꾼다
--      (판매자 계정은 바뀌지 않도록 role = 'USER' 조건)
-- UPDATE users SET role = 'ADMIN' WHERE email = '새관리자@example.com' AND role = 'USER';

-- [D5] 판매자 차단 효과: 판매자를 정지하면 그 판매자 상품은 목록에 보이지만 주문은 거절된다 (PRODUCT_NOT_PURCHASABLE)
UPDATE users SET status = 'SUSPENDED' WHERE email = 'store.c@demo.com';
-- 되돌리기
UPDATE users SET status = 'ACTIVE' WHERE email = 'store.c@demo.com';


-- =====================================================================
-- [E] 동시성: 같은 옵션을 두 세션이 동시에 주문하면 한쪽이 기다린다 (비관적 잠금)
--     Workbench 탭(세션) 2개를 열고 아래 순서대로 실행한다
-- =====================================================================

-- [E1] 세션 1: 옵션 행을 잠근다 (주문 트랜잭션이 하는 일)
-- START TRANSACTION;
-- SELECT id, stock_quantity FROM product_options WHERE id = 1 FOR UPDATE;

-- [E2] 세션 2: 같은 행을 잠그려 하면 세션 1 이 끝날 때까지 기다린다 (화면이 멈춘 것처럼 보임)
-- START TRANSACTION;
-- SELECT id, stock_quantity FROM product_options WHERE id = 1 FOR UPDATE;

-- [E3] 세션 1: 차감하고 커밋 → 세션 2 가 그제야 바뀐 재고를 읽는다
-- UPDATE product_options SET stock_quantity = stock_quantity - 1 WHERE id = 1;
-- ROLLBACK;   -- 시연이므로 실제 차감은 되돌린다 (COMMIT 하면 재고 이력과 어긋난다)

-- [E4] 세션 2: 정리
-- ROLLBACK;

-- [E5] 지금 기다리는 잠금 보기 (세션 2 가 기다리는 동안 세션 3 에서)
-- SELECT * FROM performance_schema.data_lock_waits;


-- =====================================================================
-- [F] 통계 · 실행 계획 · 정합성
-- =====================================================================

-- [F1] 판매자 A의 일별 판매량·매출 (API 와 같은 기준: 취소 제외, 주문일)
SELECT DATE(sh.created_at) AS day, SUM(oi.quantity) AS qty, SUM(oi.quantity * oi.unit_price) AS revenue,
       COUNT(DISTINCT sh.order_id) AS orders
  FROM shipments sh JOIN order_items oi ON oi.shipment_id = sh.id
 WHERE sh.seller_id = @seller AND sh.status <> 'CANCELED'
   AND sh.created_at >= CURDATE() - INTERVAL 29 DAY
 GROUP BY day ORDER BY day;

-- [F2] 브랜드별 매출 (관리자, 취소 제외) — 브랜드 합계 = 플랫폼 합계
SELECT b.name AS brand, SUM(oi.quantity) AS qty, SUM(oi.quantity * oi.unit_price) AS revenue
  FROM shipments sh
  JOIN order_items oi     ON oi.shipment_id = sh.id
  JOIN product_options po ON po.id = oi.option_id
  JOIN products p         ON p.id = po.product_id
  JOIN brands b           ON b.id = p.brand_id
 WHERE sh.status <> 'CANCELED'
 GROUP BY b.id, b.name WITH ROLLUP;

-- [F3] 실행 계획: 홈 상품 목록 (먼저 등록한 순, idx_products_created) · 내 주문 목록 (커버링 인덱스)
EXPLAIN SELECT id, name, price FROM products
 WHERE status IN ('ON_SALE', 'SOLD_OUT') ORDER BY created_at, id LIMIT 20;
EXPLAIN SELECT id, created_at FROM orders WHERE user_id = @buyer ORDER BY created_at DESC LIMIT 20;
EXPLAIN SELECT COUNT(*) FROM shipments
 WHERE seller_id = @seller AND status IN ('ORDERED', 'PREPARING', 'SHIPPING');

-- [F4] 정합성 검사 21종: scripts/check-integrity.sql 을 실행한다 (모든 bad = 0 이 정상)
--   mysql ... shop < scripts/check-integrity.sql
