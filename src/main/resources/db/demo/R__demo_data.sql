-- =====================================================================
-- 시연용 데이터 (local 프로필에서만 적용, Flyway repeatable migration)
--  - 모든 계정 비밀번호: demo1234!
--  - 파일 내용이 바뀌면 다시 실행되므로, 모든 INSERT 는 NOT EXISTS 로 중복을 막는다.
--  - 주문/장바구니 데이터는 BE-2 담당이라 포함하지 않는다.
--
-- 계정
--   store.a@demo.com  판매자(APPROVED) 어반스타일  브랜드: URBAN BASIC, URBAN STEP
--   store.b@demo.com  판매자(APPROVED) 테크마켓    브랜드: TECHON, TECHON PRO(승인 대기)
--   store.c@demo.com  판매자 신청 대기(PENDING)    → 관리자 승인 시연용
--   buyer1@demo.com, buyer2@demo.com  일반 회원
-- =====================================================================

SET @pw = '{bcrypt}$2a$10$f8blmH9.acsEJjcqrTzC0OPSz2KSMG2fPcBAIzsw14BO1xOqUf6ue';

-- ----- 회원 (users.role = SELLER 는 sellers.status = APPROVED 와 일치시킨다) -----
INSERT INTO users (email, password, name, phone, role)
SELECT t.email, @pw, t.name, t.phone, t.role
FROM (SELECT 'store.a@demo.com' AS email, '김판매' AS name, '01011110001' AS phone, 'SELLER' AS role
      UNION ALL SELECT 'store.b@demo.com', '이판매', '01011110002', 'SELLER'
      UNION ALL SELECT 'store.c@demo.com', '박신청', '01011110003', 'USER'
      UNION ALL SELECT 'buyer1@demo.com',  '최구매', '01022220001', 'USER'
      UNION ALL SELECT 'buyer2@demo.com',  '정구매', '01022220002', 'USER') t
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = t.email);

-- ----- 판매자 -----
INSERT INTO sellers (user_id, business_number, company_name, representative_name, status, approved_at)
SELECT u.id, t.bn, t.company, t.rep, t.status, IF(t.status = 'APPROVED', NOW(6), NULL)
FROM (SELECT 'store.a@demo.com' AS email, '1010000001' AS bn, '어반스타일' AS company, '김판매' AS rep, 'APPROVED' AS status
      UNION ALL SELECT 'store.b@demo.com', '1010000002', '테크마켓', '이판매', 'APPROVED'
      UNION ALL SELECT 'store.c@demo.com', '1010000003', '새싹상점', '박신청', 'PENDING') t
JOIN users u ON u.email = t.email
WHERE NOT EXISTS (SELECT 1 FROM sellers s WHERE s.user_id = u.id);

-- ----- 브랜드 -----
INSERT INTO brands (seller_id, name, description, status)
SELECT s.id, t.name, t.descr, t.status
FROM (SELECT '1010000001' AS bn, 'URBAN BASIC' AS name, '데일리 베이직 의류' AS descr, 'APPROVED' AS status
      UNION ALL SELECT '1010000001', 'URBAN STEP', '스니커즈와 가방, 잡화', 'APPROVED'
      UNION ALL SELECT '1010000002', 'TECHON', '합리적인 가격의 디지털 기기', 'APPROVED'
      UNION ALL SELECT '1010000002', 'TECHON PRO', '프리미엄 라인 (승인 대기)', 'PENDING') t
JOIN sellers s ON s.business_number = t.bn
WHERE NOT EXISTS (SELECT 1 FROM brands b WHERE b.name = t.name);

-- ----- 상품 원본 목록 (임시 테이블에 두고 products / inventory / stock_history 에 같이 사용) -----
DROP TEMPORARY TABLE IF EXISTS demo_products;
CREATE TEMPORARY TABLE demo_products (
    bn          CHAR(10)     NOT NULL,
    brand       VARCHAR(100) NULL,      -- NULL = 브랜드 없는 상품
    category_id BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    price       INT          NOT NULL,
    stock       INT          NOT NULL,  -- 초기 재고 (0 = 품절 표시 시연)
    days_ago    INT          NOT NULL,  -- 등록일 분산 (최신순 정렬 시연)
    status      VARCHAR(20)  NOT NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO demo_products (bn, brand, category_id, name, price, stock, days_ago, status) VALUES
    -- 어반스타일
    ('1010000001', 'URBAN BASIC', 11, '베이직 라운드 티셔츠',   12900, 120, 58, 'ON_SALE'),
    ('1010000001', 'URBAN BASIC', 11, '오버핏 후드티',          39900,  45, 50, 'ON_SALE'),
    ('1010000001', 'URBAN BASIC', 11, '옥스포드 셔츠',          34900,  30, 41, 'ON_SALE'),
    ('1010000001', 'URBAN BASIC', 12, '와이드 데님 팬츠',       45900,  25, 37, 'ON_SALE'),
    ('1010000001', 'URBAN BASIC', 12, '조거 트레이닝 팬츠',     29900,   0, 33, 'ON_SALE'),
    ('1010000001', 'URBAN BASIC', 13, '라이트 패딩 점퍼',       89000,  15, 20, 'ON_SALE'),
    ('1010000001', 'URBAN BASIC', 13, '트렌치 코트',           129000,   8, 12, 'ON_SALE'),
    ('1010000001', 'URBAN STEP',  21, '캔버스 스니커즈',        49000,  60, 45, 'ON_SALE'),
    ('1010000001', 'URBAN STEP',  21, '러닝화 에어라이트',      79000,  35, 15, 'ON_SALE'),
    ('1010000001', 'URBAN STEP',  22, '데일리 백팩',            59000,  40, 30, 'ON_SALE'),
    ('1010000001', 'URBAN STEP',  22, '미니 크로스백',          32000,   5, 26, 'SOLD_OUT'),
    ('1010000001', 'URBAN STEP',  23, '로고 볼캡',              19000,  80, 22, 'ON_SALE'),
    ('1010000001', NULL,          23, '니트 비니',              15000,  50,  9, 'ON_SALE'),
    ('1010000001', NULL,          52, '섬유 탈취제 500ml',       8900, 200,  6, 'ON_SALE'),
    ('1010000001', NULL,          11, '시즌오프 반팔 티셔츠',    9900,  10, 55, 'HIDDEN'),
    -- 테크마켓
    ('1010000002', 'TECHON',      31, '테크온 14인치 노트북',  899000,  12, 57, 'ON_SALE'),
    ('1010000002', 'TECHON',      31, '테크온 16인치 노트북', 1290000,   6, 40, 'ON_SALE'),
    ('1010000002', 'TECHON',      32, '테크온 스마트폰 S',     699000,  20, 35, 'ON_SALE'),
    ('1010000002', 'TECHON',      32, '스마트폰 투명 케이스',   12000, 300, 28, 'ON_SALE'),
    ('1010000002', 'TECHON',      33, '무선 이어버드',          89000,  70, 25, 'ON_SALE'),
    ('1010000002', 'TECHON',      33, '블루투스 스피커',        59000,   0, 18, 'ON_SALE'),
    ('1010000002', 'TECHON',      33, '노이즈캔슬링 헤드폰',   249000,  10,  8, 'ON_SALE'),
    ('1010000002', NULL,          31, '알루미늄 노트북 거치대', 29000,  90, 31, 'ON_SALE'),
    ('1010000002', NULL,          41, '제주 감귤 3kg',          19900, 100, 14, 'ON_SALE'),
    ('1010000002', NULL,          42, '즉석 컵밥 6개입',        12900, 150, 11, 'ON_SALE'),
    ('1010000002', NULL,          43, '탄산수 500ml 20병',      15900,  80,  7, 'ON_SALE'),
    ('1010000002', NULL,          43, '콜드브루 원액 1L',       13900,  60,  4, 'ON_SALE'),
    ('1010000002', NULL,          51, '스테인리스 프라이팬',    34900,  45, 21, 'ON_SALE'),
    ('1010000002', NULL,          51, '실리콘 조리도구 5종',    18900,  70, 16, 'ON_SALE'),
    ('1010000002', NULL,          53, '접이식 원목 테이블',     79000,   9,  3, 'ON_SALE'),
    ('1010000002', NULL,          52, '대용량 물티슈 10팩',     11900,   0,  2, 'ON_SALE');

-- 브랜드는 같은 판매자의 것만 조인되도록 seller_id 도 조건에 둔다 (복합 FK 와 같은 규칙)
INSERT INTO products (seller_id, brand_id, category_id, name, price, status, created_at, updated_at)
SELECT s.id, b.id, t.category_id, t.name, t.price, t.status,
       NOW(6) - INTERVAL t.days_ago DAY, NOW(6) - INTERVAL t.days_ago DAY
FROM demo_products t
JOIN sellers s ON s.business_number = t.bn
LEFT JOIN brands b ON b.name = t.brand AND b.seller_id = s.id
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.seller_id = s.id AND p.name = t.name);

-- 모든 상품은 재고 행을 가진다 (상품 등록 트랜잭션과 같은 규칙)
INSERT INTO inventory (product_id, quantity)
SELECT p.id, t.stock
FROM demo_products t
JOIN sellers s ON s.business_number = t.bn
JOIN products p ON p.seller_id = s.id AND p.name = t.name
WHERE NOT EXISTS (SELECT 1 FROM inventory i WHERE i.product_id = p.id);

-- 초기 재고 > 0 이면 입고(IN) 이력 → inventory.quantity = 마지막 이력의 quantity_after
INSERT INTO stock_history (product_id, change_qty, quantity_after, type, memo, created_at)
SELECT p.id, t.stock, t.stock, 'IN', '초기 재고', p.created_at
FROM demo_products t
JOIN sellers s ON s.business_number = t.bn
JOIN products p ON p.seller_id = s.id AND p.name = t.name
WHERE t.stock > 0
  AND NOT EXISTS (SELECT 1 FROM stock_history h WHERE h.product_id = p.id);

DROP TEMPORARY TABLE IF EXISTS demo_products;
