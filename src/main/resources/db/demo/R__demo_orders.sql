-- =====================================================================
-- 시연용 주문 데이터 (local 프로필에서만, R__demo_data 다음에 실행)
--
--  - 최근 90일에 걸친 주문 300건 (최근일수록 조금 더 많이) → 판매 통계(일/주/월) 차트 시연용
--  - 구매자 8명(buyer1~8@demo.com, 비밀번호 demo1234!), 주문당 1~3개 옵션
--  - 판매자별 배송(shipments) 상태는 주문 나이에 따라: 오래된 주문은 대부분 구매확정, 최근 주문은 배송준비·주문완료
--  - 약 8%는 주문완료 직후 취소 → 재고 복원(CANCEL 이력)
--  - 재고 정합성: 판매 전에 "시연용 추가 입고"(IN)로 판매량만큼 채운 뒤 ORDER / CANCEL 이력을 시간순으로 남긴다.
--    그래서 어느 시점에도 재고가 음수가 되지 않고, 옵션 재고 = 마지막 이력의 quantity_after 가 항상 맞다.
--  - 주문 가능한 옵션: 시연 브랜드의 판매중(ON_SALE) 상품, 재고가 있는 옵션, 주문 시각보다 1시간 이상 먼저 등록된 상품
--
-- 한 번만 만든다: 시연 주문(address_detail = '시연 주문 N')이 하나라도 있으면 아무것도 넣지 않는다.
-- 다시 만들려면 DB 를 초기화한다 (Docker: docker compose down -v).
-- =====================================================================

SET @pw = '{bcrypt}$2a$10$f8blmH9.acsEJjcqrTzC0OPSz2KSMG2fPcBAIzsw14BO1xOqUf6ue';

-- ----- 구매자 추가 (buyer1, buyer2 는 R__demo_data 에 있음) -----
INSERT INTO users (email, password, name, phone, role)
SELECT t.email, @pw, t.name, t.phone, 'USER'
FROM (SELECT 'buyer3@demo.com' AS email, '강구매' AS name, '01022220003' AS phone
      UNION ALL SELECT 'buyer4@demo.com', '윤구매', '01022220004'
      UNION ALL SELECT 'buyer5@demo.com', '장구매', '01022220005'
      UNION ALL SELECT 'buyer6@demo.com', '임구매', '01022220006'
      UNION ALL SELECT 'buyer7@demo.com', '한구매', '01022220007'
      UNION ALL SELECT 'buyer8@demo.com', '오구매', '01022220008') t
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = t.email);

-- ----- 한 번만 실행 -----
DROP TEMPORARY TABLE IF EXISTS demo_go;
CREATE TEMPORARY TABLE demo_go AS
SELECT NOT EXISTS (SELECT 1 FROM orders o WHERE o.address_detail LIKE '시연 주문 %') AS go;

-- ----- 주문 가능한 옵션: 상품 등록 순으로 번호(rn)를 매긴다 -----
-- 주문 시각 D 에 살 수 있는 옵션 = 등록 시각 <= D - 1시간 인 옵션 = rn 이 1 ~ k(D) 인 앞부분
DROP TEMPORARY TABLE IF EXISTS demo_opt;
CREATE TEMPORARY TABLE demo_opt (
    rn            INT          NOT NULL PRIMARY KEY,
    option_id     BIGINT       NOT NULL,
    seller_id     BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    price         INT          NOT NULL,
    product_name  VARCHAR(200) NOT NULL,
    color_name    VARCHAR(30)  NOT NULL,
    size_label    VARCHAR(10)  NOT NULL,
    init_stock    INT          NOT NULL,
    INDEX (created_at),
    INDEX (option_id)
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO demo_opt (rn, option_id, seller_id, created_at, price, product_name, color_name, size_label, init_stock)
SELECT ROW_NUMBER() OVER (ORDER BY p.created_at, o.id), o.id, b.seller_id, p.created_at, p.price, p.name,
       c.name, s.label, o.stock_quantity
FROM product_options o
JOIN products p ON p.id = o.product_id
JOIN brands b   ON b.id = p.brand_id
JOIN colors c   ON c.id = o.color_id
JOIN sizes s    ON s.id = o.size_id
WHERE b.name IN ('URBAN BASIC', 'URBAN STEP', 'MOODLAB')
  AND p.status = 'ON_SALE'
  AND o.stock_quantity > 0;

-- ----- 주문 300건: 날짜·구매자·배송 상태 -----
DROP TEMPORARY TABLE IF EXISTS demo_ord;
CREATE TEMPORARY TABLE demo_ord (
    n           INT          NOT NULL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    k           INT          NOT NULL DEFAULT 0,  -- 이 시각에 살 수 있는 옵션 수
    order_id    BIGINT       NULL
);

INSERT INTO demo_ord (n, user_id, created_at, status)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 300),
buyers AS (SELECT id, ROW_NUMBER() OVER (ORDER BY email) - 1 AS bi FROM users WHERE email LIKE 'buyer_@demo.com'),
ages AS (
    -- n 이 클수록 최근. 최근 구간이 촘촘하도록 지수 1.25 로 펼친다 (주문량이 늘어나는 추세)
    SELECT n, FLOOR(90 * POW((300 - n) / 300, 1.25)) AS d FROM seq
)
SELECT a.n, b.id,
       NOW(6) - INTERVAL a.d DAY - INTERVAL MOD(a.n * 137, 600) MINUTE,
       CASE
           WHEN MOD(a.n, 13) = 0 THEN 'CANCELED'
           WHEN a.d >= 10 THEN IF(MOD(a.n, 7) = 0, 'DELIVERED', 'CONFIRMED')
           WHEN a.d >= 5  THEN IF(MOD(a.n, 2) = 0, 'DELIVERED', 'SHIPPING')
           WHEN a.d >= 2  THEN IF(MOD(a.n, 2) = 0, 'SHIPPING', 'PREPARING')
           ELSE IF(MOD(a.n, 2) = 0, 'PREPARING', 'ORDERED')
       END
FROM ages a
JOIN buyers b ON b.bi = MOD(a.n * 5, (SELECT COUNT(*) FROM users WHERE email LIKE 'buyer_@demo.com'))
JOIN demo_go g ON g.go = 1;

UPDATE demo_ord o
   SET o.k = (SELECT COUNT(*) FROM demo_opt d WHERE d.created_at <= o.created_at - INTERVAL 1 HOUR);

-- ----- 주문 라인: 주문당 1~3개 옵션, 수량 1~2 (같은 옵션이 겹치면 합친다 → UK(shipment_id, option_id)) -----
DROP TEMPORARY TABLE IF EXISTS demo_line;
CREATE TEMPORARY TABLE demo_line (
    n    INT NOT NULL,
    rn   INT NOT NULL,
    qty  INT NOT NULL,
    PRIMARY KEY (n, rn)
);

INSERT INTO demo_line (n, rn, qty)
SELECT o.n, 1 + MOD(o.n * 7 + l.i * 31 + o.n * l.i * 13, o.k) AS pick, SUM(1 + MOD(o.n + l.i, 2))
FROM demo_ord o
JOIN (SELECT 1 AS i UNION ALL SELECT 2 UNION ALL SELECT 3) l ON l.i <= 1 + MOD(o.n, 3)
WHERE o.k > 0
GROUP BY o.n, pick;

-- ----- orders: 총액 = 라인 합계 (주문 당시 가격) -----
INSERT INTO orders (user_id, total_price, receiver_name, receiver_phone, zip_code, address, address_detail,
                    created_at, updated_at)
SELECT o.user_id, t.total, u.name, COALESCE(u.phone, '01000000000'), '06978', '서울특별시 동작구 상도로 369',
       CONCAT('시연 주문 ', o.n), o.created_at, o.created_at
FROM demo_ord o
JOIN users u ON u.id = o.user_id
JOIN (SELECT l.n, SUM(d.price * l.qty) AS total FROM demo_line l JOIN demo_opt d ON d.rn = l.rn GROUP BY l.n) t
  ON t.n = o.n;

UPDATE demo_ord o JOIN orders x ON x.address_detail = CONCAT('시연 주문 ', o.n)
   SET o.order_id = x.id;

-- ----- shipments: 주문 안의 판매자마다 하나. 상태와 시각은 CHECK 에 맞게 짝을 맞춘다 -----
INSERT INTO shipments (order_id, seller_id, status, confirmed_at, canceled_at, created_at, updated_at)
SELECT DISTINCT o.order_id, d.seller_id, o.status,
       IF(o.status = 'CONFIRMED', o.created_at + INTERVAL 5 DAY, NULL),
       IF(o.status = 'CANCELED', o.created_at + INTERVAL 30 MINUTE, NULL),
       o.created_at, o.created_at
FROM demo_ord o
JOIN demo_line l ON l.n = o.n
JOIN demo_opt d  ON d.rn = l.rn
WHERE o.order_id IS NOT NULL;

-- ----- order_items: 주문 당시 값 스냅샷 -----
INSERT INTO order_items (shipment_id, option_id, product_name, color_name, size_label, unit_price, quantity)
SELECT sh.id, d.option_id, d.product_name, d.color_name, d.size_label, d.price, l.qty
FROM demo_ord o
JOIN demo_line l  ON l.n = o.n
JOIN demo_opt d   ON d.rn = l.rn
JOIN shipments sh ON sh.order_id = o.order_id AND sh.seller_id = d.seller_id;

-- ----- 재고 이력 이벤트: 추가 입고 → 주문 차감 → 취소 복원 -----
DROP TEMPORARY TABLE IF EXISTS demo_ev;
CREATE TEMPORARY TABLE demo_ev (
    seq            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    option_id      BIGINT       NOT NULL,
    at             DATETIME(6)  NOT NULL,
    change_qty     INT          NOT NULL,
    type           VARCHAR(10)  NOT NULL,
    order_item_id  BIGINT       NULL,
    memo           VARCHAR(50)  NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 판매량(취소 포함)만큼 먼저 채운다 → 어느 시점에도 재고 >= 처음 재고 >= 1
INSERT INTO demo_ev (option_id, at, change_qty, type, memo)
SELECT d.option_id, d.created_at + INTERVAL 1 SECOND, SUM(l.qty), 'IN', '시연용 추가 입고'
FROM demo_line l JOIN demo_opt d ON d.rn = l.rn
GROUP BY d.option_id, d.created_at;

INSERT INTO demo_ev (option_id, at, change_qty, type, order_item_id)
SELECT oi.option_id, o.created_at, -oi.quantity, 'ORDER', oi.id
FROM order_items oi
JOIN shipments sh ON sh.id = oi.shipment_id
JOIN demo_ord o   ON o.order_id = sh.order_id;

INSERT INTO demo_ev (option_id, at, change_qty, type, order_item_id)
SELECT oi.option_id, sh.canceled_at, oi.quantity, 'CANCEL', oi.id
FROM order_items oi
JOIN shipments sh ON sh.id = oi.shipment_id
JOIN demo_ord o   ON o.order_id = sh.order_id
WHERE sh.status = 'CANCELED';

-- 시간순으로 넣어 "옵션별 마지막 id = 가장 최근 이력"이 되게 한다. quantity_after = 처음 재고 + 누적 변동
INSERT INTO stock_history (option_id, change_qty, quantity_after, type, order_item_id, memo, created_at)
SELECT e.option_id, e.change_qty,
       d.init_stock + SUM(e.change_qty) OVER (PARTITION BY e.option_id ORDER BY e.at, e.seq),
       e.type, e.order_item_id, e.memo, e.at
FROM demo_ev e
JOIN demo_opt d ON d.option_id = e.option_id
ORDER BY e.at, e.seq;

-- 옵션 재고 = 처음 재고 + 모든 변동 (= 마지막 이력의 quantity_after)
UPDATE product_options o
JOIN (SELECT option_id, SUM(change_qty) AS delta FROM demo_ev GROUP BY option_id) x ON x.option_id = o.id
   SET o.stock_quantity = o.stock_quantity + x.delta;

DROP TEMPORARY TABLE IF EXISTS demo_ev;
DROP TEMPORARY TABLE IF EXISTS demo_line;
DROP TEMPORARY TABLE IF EXISTS demo_ord;
DROP TEMPORARY TABLE IF EXISTS demo_opt;
DROP TEMPORARY TABLE IF EXISTS demo_go;
