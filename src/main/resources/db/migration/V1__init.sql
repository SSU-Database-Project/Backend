-- =====================================================================
-- V1: 쇼핑몰 전체 스키마 (설계 근거는 docs/BE-1_계획서.md 3~4장)
-- DBMS: MySQL 8.0.16+ (CHECK 제약 강제)
-- =====================================================================

-- ===== 회원 / 판매자 / 브랜드 =====
CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(100) NOT NULL,
    password    VARCHAR(100) NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    phone       VARCHAR(20)  NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users        PRIMARY KEY (id),
    CONSTRAINT uk_users_email  UNIQUE (email),
    CONSTRAINT ck_users_role   CHECK (role   IN ('USER', 'SELLER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sellers (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    business_number     CHAR(10)     NOT NULL,
    company_name        VARCHAR(100) NOT NULL,
    representative_name VARCHAR(50)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reject_reason       VARCHAR(255) NULL,
    approved_at         DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_sellers                 PRIMARY KEY (id),
    CONSTRAINT uk_sellers_user            UNIQUE (user_id),
    CONSTRAINT uk_sellers_business_number UNIQUE (business_number),
    CONSTRAINT fk_sellers_user            FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_sellers_status          CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_sellers_business_number CHECK (business_number REGEXP '^[0-9]{10}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE brands (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    seller_id   BIGINT        NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(1000) NULL,
    logo_url    VARCHAR(500)  NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_brands           PRIMARY KEY (id),
    CONSTRAINT uk_brands_name      UNIQUE (name),
    CONSTRAINT uk_brands_id_seller UNIQUE (id, seller_id),      -- products 복합 FK의 참조 대상
    CONSTRAINT fk_brands_seller    FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT ck_brands_status    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ===== 카테고리 / 상품 / 재고 =====
CREATE TABLE categories (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    parent_id     BIGINT      NULL,
    name          VARCHAR(50) NOT NULL,
    display_order INT         NOT NULL DEFAULT 0,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_categories             PRIMARY KEY (id),
    CONSTRAINT uk_categories_parent_name UNIQUE (parent_id, name),
    CONSTRAINT fk_categories_parent      FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id   BIGINT       NOT NULL,
    brand_id    BIGINT       NULL,
    category_id BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    price       INT          NOT NULL,
    image_url   VARCHAR(500) NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ON_SALE',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_products              PRIMARY KEY (id),
    CONSTRAINT fk_products_seller       FOREIGN KEY (seller_id) REFERENCES sellers (id),
    -- 브랜드가 있으면 (brand_id, seller_id) 쌍이 brands에 존재해야 한다 = 브랜드 소유자와 상품 소유자가 같다
    CONSTRAINT fk_products_brand_seller FOREIGN KEY (brand_id, seller_id) REFERENCES brands (id, seller_id),
    CONSTRAINT fk_products_category     FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_products_price        CHECK (price >= 0),
    CONSTRAINT ck_products_status       CHECK (status IN ('ON_SALE', 'SOLD_OUT', 'HIDDEN', 'BLOCKED')),
    INDEX idx_products_category_status_created (category_id, status, created_at),
    INDEX idx_products_seller_created          (seller_id, created_at),
    INDEX idx_products_status_price            (status, price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory (
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL DEFAULT 0,
    version    BIGINT      NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_inventory          PRIMARY KEY (product_id),
    CONSTRAINT fk_inventory_product  FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_inventory_quantity CHECK (quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ===== 장바구니 =====
CREATE TABLE carts (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_carts      PRIMARY KEY (id),
    CONSTRAINT uk_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cart_items (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    cart_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_cart_items              PRIMARY KEY (id),
    CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT fk_cart_items_cart         FOREIGN KEY (cart_id)    REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product      FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_cart_items_quantity     CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ===== 주문 =====
CREATE TABLE orders (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PAID',
    total_price    BIGINT       NOT NULL,
    receiver_name  VARCHAR(50)  NOT NULL,
    receiver_phone VARCHAR(20)  NOT NULL,
    zip_code       VARCHAR(10)  NOT NULL,
    address        VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255) NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    canceled_at    DATETIME(6)  NULL,
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_orders             PRIMARY KEY (id),
    CONSTRAINT fk_orders_user        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_orders_status      CHECK (status IN ('PAID', 'SHIPPING', 'DELIVERED', 'CANCELED')),
    CONSTRAINT ck_orders_total_price CHECK (total_price >= 0),
    CONSTRAINT ck_orders_canceled_at CHECK ((status = 'CANCELED') = (canceled_at IS NOT NULL)),
    INDEX idx_orders_user_created   (user_id, created_at),
    INDEX idx_orders_created_status (created_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_items (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_id     BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    seller_id    BIGINT       NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    unit_price   INT          NOT NULL,
    quantity     INT          NOT NULL,
    CONSTRAINT pk_order_items               PRIMARY KEY (id),
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT fk_order_items_order         FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_order_items_product       FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_order_items_seller        FOREIGN KEY (seller_id)  REFERENCES sellers (id),
    CONSTRAINT ck_order_items_unit_price    CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_quantity      CHECK (quantity > 0),
    INDEX idx_order_items_product      (product_id),
    INDEX idx_order_items_seller_order (seller_id, order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ===== 재고 이력 (order_items 참조 때문에 마지막에 생성) =====
CREATE TABLE stock_history (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    product_id     BIGINT       NOT NULL,
    change_qty     INT          NOT NULL,
    quantity_after INT          NOT NULL,
    type           VARCHAR(20)  NOT NULL,
    order_item_id  BIGINT       NULL,
    memo           VARCHAR(255) NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_stock_history            PRIMARY KEY (id),
    CONSTRAINT fk_stock_history_product    FOREIGN KEY (product_id)    REFERENCES products (id),
    CONSTRAINT fk_stock_history_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT ck_stock_history_type       CHECK (type IN ('IN', 'ORDER', 'CANCEL', 'ADJUST')),
    CONSTRAINT ck_stock_history_qty        CHECK (change_qty <> 0 AND quantity_after >= 0),
    -- 유형별 부호: 입고·취소는 +, 주문은 -, 조정은 양방향
    CONSTRAINT ck_stock_history_sign       CHECK (
        (type IN ('IN', 'CANCEL') AND change_qty > 0) OR
        (type = 'ORDER'           AND change_qty < 0) OR
        (type = 'ADJUST')),
    -- 주문/취소 이력은 원인 주문 라인이 반드시 있고, 입고/조정은 없어야 한다
    CONSTRAINT ck_stock_history_order_ref  CHECK (
        (type IN ('ORDER', 'CANCEL') AND order_item_id IS NOT NULL) OR
        (type IN ('IN', 'ADJUST')    AND order_item_id IS NULL)),
    INDEX idx_stock_history_product_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
