-- =====================================================================
-- 시연용 데이터 (local 프로필에서만 적용, Flyway repeatable migration)
--  - 모든 계정 비밀번호: demo1234!
--  - 파일 내용이 바뀌면 다시 실행되므로, 모든 INSERT 는 NOT EXISTS 로 중복을 막는다.
--  - 주문/장바구니 데이터는 BE-2 담당이라 포함하지 않는다.
--  - 상품 이미지는 품목(소분류)별 샘플 이미지: /images/samples/{품목}.jpg
--    파일은 src/main/resources/static/images/samples/ 에 넣는다.
--
-- 계정
--   store.a@demo.com  판매자  어반스타일  브랜드: URBAN BASIC, URBAN STEP
--   store.b@demo.com  판매자  무드랩      브랜드: MOODLAB, MOODLAB ATELIER(승인 대기)
--   store.c@demo.com  판매자  새싹상점    브랜드 없음 → 브랜드 등록·승인 시연용
--   buyer1@demo.com, buyer2@demo.com  일반 회원
-- =====================================================================

SET @pw = '{bcrypt}$2a$10$f8blmH9.acsEJjcqrTzC0OPSz2KSMG2fPcBAIzsw14BO1xOqUf6ue';

-- ----- 회원 (판매자는 가입 때 선택 → role = SELLER 와 sellers 행이 함께 존재) -----
INSERT INTO users (email, password, name, phone, role)
SELECT t.email, @pw, t.name, t.phone, t.role
FROM (SELECT 'store.a@demo.com' AS email, '김판매' AS name, '01011110001' AS phone, 'SELLER' AS role
      UNION ALL SELECT 'store.b@demo.com', '이판매', '01011110002', 'SELLER'
      UNION ALL SELECT 'store.c@demo.com', '박판매', '01011110003', 'SELLER'
      UNION ALL SELECT 'buyer1@demo.com',  '최구매', '01022220001', 'USER'
      UNION ALL SELECT 'buyer2@demo.com',  '정구매', '01022220002', 'USER') t
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = t.email);

-- ----- 판매자 -----
INSERT INTO sellers (user_id, business_number, company_name, representative_name)
SELECT u.id, t.bn, t.company, t.rep
FROM (SELECT 'store.a@demo.com' AS email, '1010000001' AS bn, '어반스타일' AS company, '김판매' AS rep
      UNION ALL SELECT 'store.b@demo.com', '1010000002', '무드랩',   '이판매'
      UNION ALL SELECT 'store.c@demo.com', '1010000003', '새싹상점', '박판매') t
JOIN users u ON u.email = t.email
WHERE NOT EXISTS (SELECT 1 FROM sellers s WHERE s.user_id = u.id);

-- V4 이전 시연 데이터(디지털 판매자)가 남아 있는 DB 를 패션 시연 데이터로 맞춘다 (없으면 영향 없음)
UPDATE sellers SET company_name = '무드랩' WHERE business_number = '1010000002' AND company_name = '테크마켓';
UPDATE sellers SET company_name = '새싹상점', representative_name = '박판매' WHERE business_number = '1010000003';

-- ----- 브랜드 -----
INSERT INTO brands (seller_id, name, description, status)
SELECT s.id, t.name, t.descr, t.status
FROM (SELECT '1010000001' AS bn, 'URBAN BASIC' AS name, '데일리 베이직 의류' AS descr, 'APPROVED' AS status
      UNION ALL SELECT '1010000001', 'URBAN STEP',      '스니커즈와 가방, 잡화',     'APPROVED'
      UNION ALL SELECT '1010000002', 'MOODLAB',         '미니멀 무드의 여성 캐주얼', 'APPROVED'
      UNION ALL SELECT '1010000002', 'MOODLAB ATELIER', '프리미엄 라인 (승인 대기)', 'PENDING') t
JOIN sellers s ON s.business_number = t.bn
WHERE NOT EXISTS (SELECT 1 FROM brands b WHERE b.name = t.name);

-- ----- 상품 원본 목록 (임시 테이블에 두고 products / product_options / stock_history 에 같이 사용) -----
DROP TEMPORARY TABLE IF EXISTS demo_products;
CREATE TEMPORARY TABLE demo_products (
    brand        VARCHAR(100) NOT NULL,
    category_id  BIGINT       NOT NULL,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    price        INT          NOT NULL,
    gender       VARCHAR(10)  NOT NULL,
    season       CHAR(4)      NOT NULL,
    ship_days    INT          NOT NULL,
    image        VARCHAR(50)  NOT NULL,  -- 품목별 샘플 이미지 파일명
    colors       VARCHAR(50)  NOT NULL,  -- colors.id 목록 (FIND_IN_SET)
    stock_mode   VARCHAR(10)  NOT NULL,  -- normal | zero (모든 옵션 재고 0 → 품절 표시 시연)
    days_ago     INT          NOT NULL,  -- 등록일 분산 (등록순 정렬 시연)
    status       VARCHAR(20)  NOT NULL,
    reject_reason VARCHAR(255) NULL
) DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO demo_products
    (brand, category_id, code, name, price, gender, season, ship_days, image, colors, stock_mode, days_ago, status, reject_reason)
VALUES
    -- URBAN BASIC (어반스타일)
    ('URBAN BASIC', 11, 'UB-24SS-T01', '베이직 라운드 반팔 티셔츠', 12900, 'UNISEX', '24SS', 2, 'tee.jpg',       '1,2,3', 'normal', 100, 'ON_SALE', NULL),
    ('URBAN BASIC', 11, 'UB-24SS-T02', '오버핏 그래픽 반팔 티셔츠', 19900, 'UNISEX', '24SS', 2, 'tee.jpg',       '1,2',   'normal', 97, 'ON_SALE', NULL),
    ('URBAN BASIC', 12, 'UB-24FW-L01', '헤비 코튼 긴소매 티셔츠',   24900, 'UNISEX', '24FW', 3, 'longsleeve.jpg','1,2,4', 'normal', 92, 'ON_SALE', NULL),
    ('URBAN BASIC', 13, 'UB-24SS-S01', '옥스포드 버튼다운 셔츠',    34900, 'MEN',    '24SS', 3, 'shirt.jpg',     '2,10',  'normal', 88, 'ON_SALE', NULL),
    ('URBAN BASIC', 14, 'UB-24FW-K01', '램스울 크루넥 니트',        49900, 'UNISEX', '24FW', 3, 'knit.jpg',      '3,4,5', 'normal', 84, 'ON_SALE', NULL),
    ('URBAN BASIC', 15, 'UB-24FW-H01', '오버핏 기모 후드티',        45900, 'UNISEX', '24FW', 2, 'hoodie.jpg',    '1,3,7', 'normal', 80, 'ON_SALE', NULL),
    ('URBAN BASIC', 21, 'UB-24FW-J01', '워크웨어 코튼 자켓',        89000, 'MEN',    '24FW', 5, 'jacket.jpg',    '6,7',   'normal', 76, 'ON_SALE', NULL),
    ('URBAN BASIC', 23, 'UB-24FW-P01', '라이트 숏 패딩',           129000, 'UNISEX', '24FW', 5, 'padding.jpg',   '1,4',   'normal', 70, 'ON_SALE', NULL),
    ('URBAN BASIC', 31, 'UB-24SS-D01', '와이드 데님 팬츠',          45900, 'UNISEX', '24SS', 3, 'denim.jpg',     '4,10',  'normal', 67, 'ON_SALE', NULL),
    ('URBAN BASIC', 33, 'UB-24FW-TR1', '조거 트레이닝 팬츠',        29900, 'UNISEX', '24FW', 2, 'training.jpg',  '1,3',   'zero',   64, 'ON_SALE', NULL),
    ('URBAN BASIC', 34, 'UB-24SS-SH1', '나일론 숏 팬츠',            25900, 'MEN',    '24SS', 2, 'shorts.jpg',    '1,7',   'normal', 60, 'SOLD_OUT', NULL),
    ('URBAN BASIC', 22, 'UB-24FW-C01', '울 블렌드 싱글 코트',      159000, 'MEN',    '24FW', 7, 'coat.jpg',      '1,5',   'normal',  3, 'PENDING', NULL),
    -- URBAN STEP (어반스타일)
    ('URBAN STEP',  51, 'US-24SS-SN1', '캔버스 로우 스니커즈',      49000, 'UNISEX', '24SS', 3, 'sneakers.jpg',  '1,2',   'normal', 95, 'ON_SALE', NULL),
    ('URBAN STEP',  51, 'US-24FW-SN2', '레더 코트 스니커즈',        79000, 'UNISEX', '24FW', 3, 'sneakers.jpg',  '2,5',   'normal', 73, 'ON_SALE', NULL),
    ('URBAN STEP',  52, 'US-24FW-LF1', '페니 로퍼',                 89000, 'MEN',    '24FW', 5, 'loafers.jpg',   '1,6',   'normal', 58, 'ON_SALE', NULL),
    ('URBAN STEP',  61, 'US-24SS-BP1', '데일리 코듀라 백팩',        59000, 'UNISEX', '24SS', 2, 'backpack.jpg',  '1,4',   'normal', 86, 'ON_SALE', NULL),
    ('URBAN STEP',  62, 'US-24SS-CB1', '미니 크로스백',             32000, 'WOMEN',  '24SS', 2, 'crossbag.jpg',  '1,5,9', 'normal', 78, 'ON_SALE', NULL),
    ('URBAN STEP',  71, 'US-24SS-CP1', '로고 자수 볼캡',            19000, 'UNISEX', '24SS', 1, 'cap.jpg',       '1,2,4', 'normal', 82, 'ON_SALE', NULL),
    ('URBAN STEP',  72, 'US-24FW-BN1', '골지 니트 비니',            15000, 'UNISEX', '24FW', 1, 'beanie.jpg',    '1,3,8', 'normal', 55, 'ON_SALE', NULL),
    ('URBAN STEP',  81, 'US-24FW-SK1', '코튼 크루 삭스 3팩',         9900, 'UNISEX', '24FW', 1, 'socks.jpg',     '1,2',   'normal', 50, 'ON_SALE', NULL),
    -- MOODLAB (무드랩)
    ('MOODLAB',     13, 'ML-24SS-B01', '린넨 오버 블라우스',        42000, 'WOMEN',  '24SS', 3, 'shirt.jpg',     '2,5',   'normal', 90, 'ON_SALE', NULL),
    ('MOODLAB',     24, 'ML-24FW-CD1', '알파카 브이넥 가디건',      69000, 'WOMEN',  '24FW', 4, 'cardigan.jpg',  '5,9,3', 'normal', 75, 'ON_SALE', NULL),
    ('MOODLAB',     32, 'ML-24FW-SL1', '하이웨이스트 와이드 슬랙스', 52000, 'WOMEN',  '24FW', 3, 'slacks.jpg',    '1,3,5', 'normal', 68, 'ON_SALE', NULL),
    ('MOODLAB',     41, 'ML-24SS-OP1', '플리츠 롱 원피스',          79000, 'WOMEN',  '24SS', 4, 'dress.jpg',     '1,4',   'normal', 62, 'ON_SALE', NULL),
    ('MOODLAB',     42, 'ML-24SS-SK1', 'A라인 미디 스커트',         45000, 'WOMEN',  '24SS', 3, 'skirt.jpg',     '1,5,6', 'normal', 52, 'ON_SALE', NULL),
    ('MOODLAB',     22, 'ML-24FW-CT1', '핸드메이드 울 롱 코트',    219000, 'WOMEN',  '24FW', 7, 'coat.jpg',      '5,6',   'normal',  2, 'PENDING', NULL),
    ('MOODLAB',     11, 'ML-24SS-T01', '슬림 크롭 반팔 티셔츠',     15900, 'WOMEN',  '24SS', 2, 'tee.jpg',       '2,9',   'normal',  5, 'REJECTED', '상품 설명에 소재 정보가 없습니다. 소재와 세탁 방법을 추가해 주세요.');

-- 브랜드명으로 연결. 판매자는 브랜드에서 정해진다(products 에 seller_id 없음)
INSERT INTO products (brand_id, category_id, product_code, name, description, price, gender, season, ship_days,
                      image_url, status, reject_reason, created_at, updated_at)
SELECT b.id, t.category_id, t.code, t.name, CONCAT(t.name, ' — 시연용 상품입니다.'), t.price, t.gender, t.season,
       t.ship_days, CONCAT('/images/samples/', t.image), t.status, t.reject_reason,
       NOW(6) - INTERVAL t.days_ago DAY, NOW(6) - INTERVAL t.days_ago DAY
FROM demo_products t
JOIN brands b ON b.name = t.brand
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.brand_id = b.id AND p.product_code = t.code);

-- ----- 옵션 = 색상 × (카테고리 사이즈 체계의 사이즈). 의류는 S~XL, 신발은 230~280, 잡화는 FREE -----
-- 재고는 결정적인 식으로 분산시키고, 일부 옵션은 0 (사이즈별 품절 시연)
INSERT INTO product_options (product_id, color_id, size_id, stock_quantity, created_at, updated_at)
SELECT p.id, c.id, s.id,
       IF(t.stock_mode = 'zero', 0, MOD(p.id * 7 + c.id * 5 + s.id * 3, 13)),
       p.created_at, p.created_at
FROM demo_products t
JOIN brands b      ON b.name = t.brand
JOIN products p    ON p.brand_id = b.id AND p.product_code = t.code
JOIN categories ct ON ct.id = p.category_id
JOIN colors c      ON FIND_IN_SET(c.id, t.colors) > 0
JOIN sizes s       ON s.size_group = ct.size_group
                  AND (s.size_group <> 'CLOTHING' OR s.label IN ('S', 'M', 'L', 'XL'))
                  AND (s.size_group <> 'SHOES'    OR s.label BETWEEN '230' AND '280')
WHERE NOT EXISTS (SELECT 1 FROM product_options o WHERE o.product_id = p.id);

-- 초기 재고 > 0 이면 입고(IN) 이력 → 옵션 재고 = 마지막 이력의 quantity_after
INSERT INTO stock_history (option_id, change_qty, quantity_after, type, memo, created_at)
SELECT o.id, o.stock_quantity, o.stock_quantity, 'IN', '초기 재고', o.created_at
FROM product_options o
JOIN products p ON p.id = o.product_id
JOIN brands b   ON b.id = p.brand_id
WHERE b.name IN ('URBAN BASIC', 'URBAN STEP', 'MOODLAB')
  AND o.stock_quantity > 0
  AND NOT EXISTS (SELECT 1 FROM stock_history h WHERE h.option_id = o.id);

-- ----- 좋아요 (마이페이지 좋아요 목록 시연) -----
INSERT INTO likes (user_id, product_id)
SELECT u.id, p.id
FROM (SELECT 'buyer1@demo.com' AS email, 'UB-24FW-H01' AS code
      UNION ALL SELECT 'buyer1@demo.com', 'US-24SS-SN1'
      UNION ALL SELECT 'buyer1@demo.com', 'ML-24FW-CD1'
      UNION ALL SELECT 'buyer2@demo.com', 'UB-24FW-H01'
      UNION ALL SELECT 'buyer2@demo.com', 'ML-24SS-OP1') t
JOIN users u    ON u.email = t.email
JOIN products p ON p.product_code = t.code
WHERE NOT EXISTS (SELECT 1 FROM likes l WHERE l.user_id = u.id AND l.product_id = p.id);

DROP TEMPORARY TABLE IF EXISTS demo_products;
