-- =====================================================================
-- V4: 패션 전용 쇼핑몰로 재설계 (2026-11-10 회의 결정 반영)
--
--  - 가입 시 일반회원/판매자 선택 → 판매자 승인 단계 제거 (sellers.status 삭제)
--  - 회원 탈퇴 처리 (users.status = WITHDRAWN)
--  - 모든 상품은 브랜드 소속 (brand_id NOT NULL) → products.seller_id 는 brand 로 도출되므로 제거 (3NF)
--  - 상품도 관리자 승인 (PENDING → ON_SALE / REJECTED), 판매자 삭제는 DELETED (승인 불필요)
--  - 상품 속성: 품번, 성별, 시즌(24FW), 출고 예정일수, 대표 이미지
--  - 색상·사이즈는 미리 정한 목록(colors, sizes) 중에서 선택, 재고는 옵션(색상×사이즈) 단위 (product_options)
--  - 장바구니 체크 상태 저장 (cart_items.selected), 장바구니·주문은 옵션 단위
--  - 배송 상태는 판매자별 (shipments: 주문 1 : 판매자별 배송 N), 판매자별 취소
--  - 좋아요 (likes)
--  - 카테고리는 패션 분류로 고정 (관리자 카테고리 관리 기능 제거)
--
-- 상품·주문 관련 테이블은 구조가 크게 바뀌어 기존 행을 옮길 수 없다. 이 시점의 DB 에는 개발·시연용 데이터만
-- 있으므로 해당 테이블을 지우고 새로 만든다. (users, sellers, brands, carts 는 행을 유지한 채 변경)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 구조가 바뀌는 테이블 제거 (FK 의존 순서: 자식 → 부모)
-- ---------------------------------------------------------------------
DROP TABLE stock_history;
DROP TABLE order_items;
DROP TABLE orders;
DROP TABLE cart_items;
DROP TABLE inventory;
DROP TABLE products;

-- ---------------------------------------------------------------------
-- 2. users: 탈퇴 상태 추가
--    탈퇴 = 로그인 불가 + 개인정보 삭제(이메일·이름·연락처 마스킹), 주문 기록은 유지
-- ---------------------------------------------------------------------
ALTER TABLE users DROP CHECK ck_users_status;
ALTER TABLE users
    ADD COLUMN withdrawn_at DATETIME(6) NULL AFTER status,
    ADD CONSTRAINT ck_users_status    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    -- 탈퇴 상태와 탈퇴 시각은 항상 짝을 이룬다
    ADD CONSTRAINT ck_users_withdrawn CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL));

-- ---------------------------------------------------------------------
-- 3. sellers: 승인 단계 제거
--    가입할 때 판매자를 선택하면 바로 판매자다. 기존 신청 행(대기·거절)도 판매자로 전환한다.
-- ---------------------------------------------------------------------
UPDATE users u JOIN sellers s ON s.user_id = u.id
   SET u.role = 'SELLER'
 WHERE u.role = 'USER';

ALTER TABLE sellers DROP CHECK ck_sellers_status;
ALTER TABLE sellers
    DROP COLUMN status,
    DROP COLUMN reject_reason,
    DROP COLUMN approved_at;

-- ---------------------------------------------------------------------
-- 4. brands: 거절 사유 추가, 복합 FK 참조용 UNIQUE 제거
--    상품이 브랜드를 반드시 갖게 되어 products.seller_id 가 사라지므로 (id, seller_id) UNIQUE 가 필요 없다.
-- ---------------------------------------------------------------------
ALTER TABLE brands
    DROP INDEX uk_brands_id_seller,
    ADD COLUMN reject_reason VARCHAR(255) NULL AFTER status;

-- ---------------------------------------------------------------------
-- 5. categories: 패션 분류로 교체 + 사이즈 체계
--    size_group: 이 분류의 상품이 쓰는 사이즈 목록 (CLOTHING = XS~XXL, SHOES = 220~300, FREE = FREE)
-- ---------------------------------------------------------------------
DELETE FROM categories WHERE parent_id IS NOT NULL;
DELETE FROM categories;

ALTER TABLE categories
    ADD COLUMN size_group VARCHAR(20) NOT NULL AFTER name,
    ADD CONSTRAINT ck_categories_size_group CHECK (size_group IN ('CLOTHING', 'SHOES', 'FREE'));

INSERT INTO categories (id, parent_id, name, size_group, display_order) VALUES
    (1, NULL, '상의',          'CLOTHING', 1),
    (2, NULL, '아우터',        'CLOTHING', 2),
    (3, NULL, '바지',          'CLOTHING', 3),
    (4, NULL, '원피스/스커트', 'CLOTHING', 4),
    (5, NULL, '신발',          'SHOES',    5),
    (6, NULL, '가방',          'FREE',     6),
    (7, NULL, '모자',          'FREE',     7),
    (8, NULL, '액세서리',      'FREE',     8);

INSERT INTO categories (id, parent_id, name, size_group, display_order) VALUES
    (11, 1, '반소매 티셔츠', 'CLOTHING', 1),
    (12, 1, '긴소매 티셔츠', 'CLOTHING', 2),
    (13, 1, '셔츠/블라우스', 'CLOTHING', 3),
    (14, 1, '니트/스웨터',   'CLOTHING', 4),
    (15, 1, '후드/맨투맨',   'CLOTHING', 5),
    (21, 2, '자켓',          'CLOTHING', 1),
    (22, 2, '코트',          'CLOTHING', 2),
    (23, 2, '패딩',          'CLOTHING', 3),
    (24, 2, '가디건',        'CLOTHING', 4),
    (31, 3, '데님 팬츠',     'CLOTHING', 1),
    (32, 3, '슬랙스',        'CLOTHING', 2),
    (33, 3, '트레이닝 팬츠', 'CLOTHING', 3),
    (34, 3, '숏 팬츠',       'CLOTHING', 4),
    (41, 4, '원피스',        'CLOTHING', 1),
    (42, 4, '스커트',        'CLOTHING', 2),
    (51, 5, '스니커즈',      'SHOES',    1),
    (52, 5, '구두/로퍼',     'SHOES',    2),
    (53, 5, '샌들/슬리퍼',   'SHOES',    3),
    (54, 5, '부츠',          'SHOES',    4),
    (61, 6, '백팩',          'FREE',     1),
    (62, 6, '크로스백',      'FREE',     2),
    (63, 6, '토트백',        'FREE',     3),
    (71, 7, '캡/야구모자',   'FREE',     1),
    (72, 7, '비니',          'FREE',     2),
    (73, 7, '버킷햇',        'FREE',     3),
    (81, 8, '양말',          'FREE',     1),
    (82, 8, '벨트',          'FREE',     2),
    (83, 8, '주얼리',        'FREE',     3);

-- ---------------------------------------------------------------------
-- 6. 색상 / 사이즈 목록 (참조 테이블)
--    판매자는 자유 입력 대신 이 목록에서 고른다 → "블랙"과 "black" 같은 표기 차이 없이 색상별 필터가 가능
-- ---------------------------------------------------------------------
CREATE TABLE colors (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(30)  NOT NULL,
    hex_code       CHAR(7)      NOT NULL,            -- 화면 표시용 색상 칩 (#RRGGBB)
    display_order  INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_colors          PRIMARY KEY (id),
    CONSTRAINT uk_colors_name     UNIQUE (name),
    CONSTRAINT ck_colors_hex_code CHECK (hex_code REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE = InnoDB;

INSERT INTO colors (id, name, hex_code, display_order) VALUES
    (1,  '블랙',   '#000000', 1),
    (2,  '화이트', '#FFFFFF', 2),
    (3,  '그레이', '#8E8E8E', 3),
    (4,  '네이비', '#1F2A44', 4),
    (5,  '베이지', '#D8C3A5', 5),
    (6,  '브라운', '#6B4423', 6),
    (7,  '카키',   '#5B5F3A', 7),
    (8,  '레드',   '#C62828', 8),
    (9,  '핑크',   '#F4A6C0', 9),
    (10, '블루',   '#1E5BB8', 10),
    (11, '그린',   '#2E7D32', 11),
    (12, '옐로우', '#F2C94C', 12);

CREATE TABLE sizes (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    size_group     VARCHAR(20)  NOT NULL,
    label          VARCHAR(10)  NOT NULL,
    display_order  INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_sizes             PRIMARY KEY (id),
    CONSTRAINT uk_sizes_group_label UNIQUE (size_group, label),
    CONSTRAINT ck_sizes_size_group  CHECK (size_group IN ('CLOTHING', 'SHOES', 'FREE'))
) ENGINE = InnoDB;

INSERT INTO sizes (id, size_group, label, display_order) VALUES
    (1,  'CLOTHING', 'XS',  1),
    (2,  'CLOTHING', 'S',   2),
    (3,  'CLOTHING', 'M',   3),
    (4,  'CLOTHING', 'L',   4),
    (5,  'CLOTHING', 'XL',  5),
    (6,  'CLOTHING', 'XXL', 6),
    (11, 'SHOES',    '220', 1),
    (12, 'SHOES',    '230', 2),
    (13, 'SHOES',    '240', 3),
    (14, 'SHOES',    '250', 4),
    (15, 'SHOES',    '260', 5),
    (16, 'SHOES',    '270', 6),
    (17, 'SHOES',    '280', 7),
    (18, 'SHOES',    '290', 8),
    (19, 'SHOES',    '300', 9),
    (21, 'FREE',     'FREE', 1);

-- ---------------------------------------------------------------------
-- 7. products: 모든 상품은 브랜드 소속, 관리자 승인 필요
-- ---------------------------------------------------------------------
CREATE TABLE products (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    brand_id       BIGINT        NOT NULL,           -- 판매자는 brands.seller_id 로 도출 (중복 저장 안 함)
    category_id    BIGINT        NOT NULL,           -- 소분류만 (서비스 검증)
    product_code   VARCHAR(50)   NOT NULL,           -- 품번. 화면의 "상품번호"
    name           VARCHAR(200)  NOT NULL,
    description    TEXT          NULL,
    price          INT           NOT NULL,
    gender         VARCHAR(10)   NOT NULL,           -- MEN / WOMEN / UNISEX
    season         CHAR(4)       NOT NULL,           -- 출시 시즌: 24SS, 24FW
    ship_days      INT           NOT NULL,           -- 주문 후 N일 내 출고
    image_url      VARCHAR(500)  NULL,               -- 대표 이미지 (서버 업로드 경로)
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    reject_reason  VARCHAR(255)  NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_products               PRIMARY KEY (id),
    -- 품번은 브랜드 안에서 유일. 브랜드 FK 인덱스 역할도 겸한다 (brand_id 가 선두 컬럼)
    CONSTRAINT uk_products_brand_code    UNIQUE (brand_id, product_code),
    CONSTRAINT fk_products_brand         FOREIGN KEY (brand_id)    REFERENCES brands (id),
    CONSTRAINT fk_products_category      FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_products_price         CHECK (price >= 0),
    CONSTRAINT ck_products_gender        CHECK (gender IN ('MEN', 'WOMEN', 'UNISEX')),
    CONSTRAINT ck_products_season        CHECK (season REGEXP '^[0-9]{2}(SS|FW)$'),
    CONSTRAINT ck_products_ship_days     CHECK (ship_days BETWEEN 0 AND 30),
    -- PENDING 승인 대기(신규·수정 후 재승인) / REJECTED 거절 / ON_SALE 판매중 / SOLD_OUT 판매자 수동 품절
    -- BLOCKED 관리자 차단 / DELETED 판매자 삭제 (주문 기록 보존을 위해 행은 남김)
    CONSTRAINT ck_products_status        CHECK (status IN ('PENDING', 'REJECTED', 'ON_SALE', 'SOLD_OUT', 'BLOCKED', 'DELETED')),
    -- 상품 목록: "등호 조건 → 정렬 컬럼" (V3 원칙 유지). 홈 기본 정렬은 먼저 등록한 순(created_at ASC)
    INDEX idx_products_category_created (category_id, created_at),
    INDEX idx_products_created          (created_at),
    INDEX idx_products_price            (price),
    -- 관리자 승인 대기 목록: PENDING 은 전체 중 소수라 status 의 선택도가 높다
    INDEX idx_products_status_created   (status, created_at)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 8. product_options: 색상 × 사이즈 조합, 재고는 옵션 단위
--    기존 inventory(상품 1:1)를 대신한다. 주문은 옵션 행만 잠그므로 상품 정보 수정과 락이 겹치지 않는다.
-- ---------------------------------------------------------------------
CREATE TABLE product_options (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    product_id      BIGINT       NOT NULL,
    color_id        BIGINT       NOT NULL,
    size_id         BIGINT       NOT NULL,
    stock_quantity  INT          NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,  -- 낙관적 락 (BE-2 동시성 방식에 따라 사용)
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_product_options              PRIMARY KEY (id),
    -- 같은 상품에 같은 색상·사이즈 조합은 한 번만
    CONSTRAINT uk_product_options_combination  UNIQUE (product_id, color_id, size_id),
    CONSTRAINT fk_product_options_product      FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_options_color        FOREIGN KEY (color_id)   REFERENCES colors (id),
    CONSTRAINT fk_product_options_size         FOREIGN KEY (size_id)    REFERENCES sizes (id),
    -- 동시 주문이 몰려도 음수 재고는 DB 가 거부 (최후 방어선)
    CONSTRAINT ck_product_options_stock        CHECK (stock_quantity >= 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 9. cart_items: 옵션 단위 + 체크 상태 저장
-- ---------------------------------------------------------------------
CREATE TABLE cart_items (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    cart_id     BIGINT       NOT NULL,
    option_id   BIGINT       NOT NULL,
    quantity    INT          NOT NULL,
    selected    BOOLEAN      NOT NULL DEFAULT TRUE,  -- 장바구니 체크 여부 (다시 들어와도 유지)
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_cart_items              PRIMARY KEY (id),
    CONSTRAINT uk_cart_items_cart_option  UNIQUE (cart_id, option_id),
    CONSTRAINT fk_cart_items_cart         FOREIGN KEY (cart_id)   REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_option       FOREIGN KEY (option_id) REFERENCES product_options (id),
    CONSTRAINT ck_cart_items_quantity     CHECK (quantity > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 10. orders: 주문 헤더 (결제 1회 = 주문 1건). 상태는 판매자별 배송(shipments)에 있다.
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    total_price     BIGINT        NOT NULL,          -- 주문 당시 전체 결제 금액 (취소돼도 바뀌지 않음)
    receiver_name   VARCHAR(50)   NOT NULL,
    receiver_phone  VARCHAR(20)   NOT NULL,
    zip_code        VARCHAR(10)   NOT NULL,
    address         VARCHAR(255)  NOT NULL,
    address_detail  VARCHAR(255)  NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_orders             PRIMARY KEY (id),
    CONSTRAINT fk_orders_user        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_orders_total_price CHECK (total_price >= 0),
    INDEX idx_orders_user_created (user_id, created_at),   -- 내 주문 목록
    INDEX idx_orders_created      (created_at)             -- 기간별 통계
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 11. shipments: 주문 안의 판매자별 배송 단위
--     장바구니에서 여러 판매자 상품을 한 번에 주문해도 배송 상태·취소·구매확정은 판매자별로 따로 진행된다.
--     ORDERED 주문완료 → PREPARING 배송준비 → SHIPPING 배송중 → DELIVERED 배송완료 → CONFIRMED 구매확정
--     CANCELED 취소 (ORDERED 에서만, 서비스 검증)
-- ---------------------------------------------------------------------
CREATE TABLE shipments (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL,
    seller_id     BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ORDERED',
    confirmed_at  DATETIME(6)  NULL,
    canceled_at   DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_shipments              PRIMARY KEY (id),
    -- 한 주문에서 판매자당 배송 묶음은 하나
    CONSTRAINT uk_shipments_order_seller UNIQUE (order_id, seller_id),
    CONSTRAINT fk_shipments_order        FOREIGN KEY (order_id)  REFERENCES orders (id),
    CONSTRAINT fk_shipments_seller       FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT ck_shipments_status       CHECK (status IN ('ORDERED', 'PREPARING', 'SHIPPING', 'DELIVERED', 'CONFIRMED', 'CANCELED')),
    -- 상태와 시각은 항상 짝을 이룬다
    CONSTRAINT ck_shipments_confirmed_at CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL)),
    CONSTRAINT ck_shipments_canceled_at  CHECK ((status = 'CANCELED')  = (canceled_at  IS NOT NULL)),
    INDEX idx_shipments_seller_created (seller_id, created_at)  -- 판매자 주문 관리 목록
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 12. order_items: 주문 상품 줄 (옵션 단위) + 주문 당시 값 스냅샷
--     주문·판매자는 shipment 로, 상품은 option 으로 도출된다 (중복 저장 안 함)
-- ---------------------------------------------------------------------
CREATE TABLE order_items (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    shipment_id   BIGINT        NOT NULL,
    option_id     BIGINT        NOT NULL,
    product_name  VARCHAR(200)  NOT NULL,   -- 스냅샷
    color_name    VARCHAR(30)   NOT NULL,   -- 스냅샷
    size_label    VARCHAR(10)   NOT NULL,   -- 스냅샷
    unit_price    INT           NOT NULL,   -- 스냅샷: 주문 당시 가격
    quantity      INT           NOT NULL,
    CONSTRAINT pk_order_items                 PRIMARY KEY (id),
    CONSTRAINT uk_order_items_shipment_option UNIQUE (shipment_id, option_id),
    CONSTRAINT fk_order_items_shipment        FOREIGN KEY (shipment_id) REFERENCES shipments (id),
    CONSTRAINT fk_order_items_option          FOREIGN KEY (option_id)   REFERENCES product_options (id),
    CONSTRAINT ck_order_items_unit_price      CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_quantity        CHECK (quantity > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 13. stock_history: 옵션 단위 재고 변동 이력 (append-only)
-- ---------------------------------------------------------------------
CREATE TABLE stock_history (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    option_id       BIGINT        NOT NULL,
    change_qty      INT           NOT NULL,
    quantity_after  INT           NOT NULL,
    type            VARCHAR(20)   NOT NULL,
    order_item_id   BIGINT        NULL,
    memo            VARCHAR(255)  NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_stock_history            PRIMARY KEY (id),
    CONSTRAINT fk_stock_history_option     FOREIGN KEY (option_id)     REFERENCES product_options (id),
    CONSTRAINT fk_stock_history_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT ck_stock_history_type       CHECK (type IN ('IN', 'ORDER', 'CANCEL', 'ADJUST')),
    CONSTRAINT ck_stock_history_qty        CHECK (change_qty <> 0 AND quantity_after >= 0),
    CONSTRAINT ck_stock_history_sign       CHECK (
        (type IN ('IN', 'CANCEL') AND change_qty > 0) OR
        (type = 'ORDER'           AND change_qty < 0) OR
        (type = 'ADJUST')),
    CONSTRAINT ck_stock_history_order_ref  CHECK (
        (type IN ('ORDER', 'CANCEL') AND order_item_id IS NOT NULL) OR
        (type IN ('IN', 'ADJUST')    AND order_item_id IS NULL)),
    INDEX idx_stock_history_option_created (option_id, created_at)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 14. likes: 회원 ↔ 상품 N:M (좋아요)
--     (user_id, product_id) 복합 PK → 같은 상품에 두 번 좋아요 불가
-- ---------------------------------------------------------------------
CREATE TABLE likes (
    user_id     BIGINT       NOT NULL,
    product_id  BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_likes         PRIMARY KEY (user_id, product_id),
    CONSTRAINT fk_likes_user    FOREIGN KEY (user_id)    REFERENCES users (id),
    CONSTRAINT fk_likes_product FOREIGN KEY (product_id) REFERENCES products (id),
    INDEX idx_likes_user_created (user_id, created_at),   -- 내 좋아요 목록 최신순
    INDEX idx_likes_product      (product_id)             -- 상품별 좋아요 수 (FK 인덱스 겸용)
) ENGINE = InnoDB;
