package com.ssu.shop.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 제약(UNIQUE / FK / CHECK / RESTRICT)이 실제 MySQL 에서 잘못된 데이터를 거부하는지 검증한다.
 * 애플리케이션 코드를 거치지 않고 SQL 을 직접 실행해 "DB 가 최후 방어선"인지 확인하는 것이 목적이다.
 * 컨텍스트가 뜨는 것 자체로 Flyway 마이그레이션 + JPA 엔티티-테이블 일치(ddl-auto=validate)도 검증된다.
 *
 * 실행: DB_TEST_URL 환경변수가 있을 때만 (예: jdbc:mysql://localhost:3308/shop_test?allowPublicKeyRetrieval=true&useSSL=false)
 * 각 테스트는 @Transactional 로 롤백된다.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class SchemaConstraintTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DB_TEST_URL"));
        registry.add("spring.datasource.username", () -> envOr("DB_TEST_USERNAME", "shop"));
        registry.add("spring.datasource.password", () -> envOr("DB_TEST_PASSWORD", "shop1234"));
        // 시연 데이터(db/demo)는 제외하고 순수 스키마 + 고정 시드만으로 검증
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    private static String envOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }

    @Autowired
    JdbcTemplate jdbc;

    // V4 고정 시드
    static final long CLOTHING_LEAF = 11L; // 상의 > 반소매 티셔츠
    static final long BLACK = 1L;
    static final long WHITE = 2L;
    static final long SIZE_M = 3L;

    long sellerA;
    long brandA;

    @BeforeEach
    void setUp() {
        sellerA = insertSeller(insertUser("seller-a@test.com", "SELLER"), "1111111111");
        jdbc.update("INSERT INTO brands (seller_id, name, status) VALUES (?, 'A브랜드', 'APPROVED')", sellerA);
        brandA = lastId();
    }

    // ===== UNIQUE =====

    @Test
    void 이메일은_대소문자만_달라도_중복으로_거부된다() {
        insertUser("dup@test.com", "USER");
        assertThatThrownBy(() -> insertUser("DUP@Test.com", "USER"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_users_email");
    }

    @Test
    void 한_회원은_판매자_정보를_하나만_가질_수_있다() {
        long userId = insertUser("one@test.com", "SELLER");
        insertSeller(userId, "3333333333");
        assertThatThrownBy(() -> insertSeller(userId, "4444444444"))
                .hasMessageContaining("uk_sellers_user");
    }

    @Test
    void 품번은_같은_브랜드_안에서_유일하다() {
        insertProduct(brandA, "CODE-1");
        assertThatThrownBy(() -> insertProduct(brandA, "CODE-1"))
                .hasMessageContaining("uk_products_brand_code");
    }

    @Test
    void 같은_상품에_같은_색상_사이즈_옵션은_하나만() {
        long productId = insertProduct(brandA, "CODE-1");
        insertOption(productId, BLACK, SIZE_M, 5);
        assertThatCode(() -> insertOption(productId, WHITE, SIZE_M, 5)).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertOption(productId, BLACK, SIZE_M, 1))
                .hasMessageContaining("uk_product_options_combination");
    }

    @Test
    void 같은_상품에_좋아요는_한_번만_복합PK() {
        long userId = insertUser("liker@test.com", "USER");
        long productId = insertProduct(brandA, "CODE-1");
        jdbc.update("INSERT INTO likes (user_id, product_id) VALUES (?, ?)", userId, productId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO likes (user_id, product_id) VALUES (?, ?)", userId, productId))
                .hasMessageContaining("PRIMARY");
    }

    @Test
    void 한_주문에서_판매자별_배송은_하나만() {
        long orderId = insertOrder(insertUser("buyer@test.com", "USER"));
        insertShipment(orderId, sellerA);
        assertThatThrownBy(() -> insertShipment(orderId, sellerA))
                .hasMessageContaining("uk_shipments_order_seller");
    }

    // ===== CHECK =====

    @Test
    void 사업자번호는_숫자_10자리만_허용된다() {
        long userId = insertUser("bn@test.com", "SELLER");
        assertThatThrownBy(() -> insertSeller(userId, "123-45-678"))
                .hasMessageContaining("ck_sellers_business_number");
    }

    @Test
    void 시즌은_연도2자리와_SS_FW_형식만_허용된다() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO products (brand_id, category_id, product_code, name, price, gender, season, ship_days)
                VALUES (?, ?, 'X', '상품', 1000, 'UNISEX', '2024', 3)""", brandA, CLOTHING_LEAF))
                .hasMessageContaining("ck_products_season");
    }

    @Test
    void 성별은_MEN_WOMEN_UNISEX만_허용된다() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO products (brand_id, category_id, product_code, name, price, gender, season, ship_days)
                VALUES (?, ?, 'X', '상품', 1000, 'KIDS', '24FW', 3)""", brandA, CLOTHING_LEAF))
                .hasMessageContaining("ck_products_gender");
    }

    @Test
    void 옵션_재고는_음수가_될_수_없다_동시성의_최후_방어선() {
        long optionId = insertOption(insertProduct(brandA, "CODE-1"), BLACK, SIZE_M, 1);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE product_options SET stock_quantity = stock_quantity - 2 WHERE id = ?", optionId))
                .hasMessageContaining("ck_product_options_stock");
    }

    @Test
    void 조건부_UPDATE는_재고가_부족하면_영향행이_0이다() {
        long optionId = insertOption(insertProduct(brandA, "CODE-1"), BLACK, SIZE_M, 1);
        int updated = jdbc.update("""
                UPDATE product_options SET stock_quantity = stock_quantity - ?
                 WHERE id = ? AND stock_quantity >= ?""", 2, optionId, 2);
        assertThat(updated).isZero();
    }

    @Test
    void 입고_이력은_양수여야_하고_주문_이력은_원인_주문라인이_있어야_한다() {
        long optionId = insertOption(insertProduct(brandA, "CODE-1"), BLACK, SIZE_M, 5);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stock_history (option_id, change_qty, quantity_after, type) VALUES (?, -5, 0, 'IN')",
                optionId))
                .hasMessageContaining("ck_stock_history_sign");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stock_history (option_id, change_qty, quantity_after, type) VALUES (?, -1, 4, 'ORDER')",
                optionId))
                .hasMessageContaining("ck_stock_history_order_ref");
    }

    @Test
    void 배송_취소_구매확정_상태와_시각은_항상_함께_존재한다() {
        long shipmentId = insertShipment(insertOrder(insertUser("cancel@test.com", "USER")), sellerA);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE shipments SET status = 'CANCELED', canceled_by = 'BUYER' WHERE id = ?", shipmentId))
                .hasMessageContaining("ck_shipments_canceled_at");
        assertThatThrownBy(() -> jdbc.update("UPDATE shipments SET status = 'CONFIRMED' WHERE id = ?", shipmentId))
                .hasMessageContaining("ck_shipments_confirmed_at");
        assertThatCode(() -> jdbc.update(
                "UPDATE shipments SET status = 'CANCELED', canceled_at = NOW(6), canceled_by = 'BUYER' WHERE id = ?",
                shipmentId))
                .doesNotThrowAnyException();
    }

    @Test
    void 탈퇴_상태와_탈퇴시각은_항상_함께_존재한다() {
        long userId = insertUser("leave@test.com", "USER");
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", userId))
                .hasMessageContaining("ck_users_withdrawn");
    }

    // ===== FK / NOT NULL =====

    @Test
    void 브랜드_없는_상품은_만들_수_없다() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO products (brand_id, category_id, product_code, name, price, gender, season, ship_days)
                VALUES (NULL, ?, 'X', '상품', 1000, 'UNISEX', '24FW', 3)""", CLOTHING_LEAF))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void 목록에_없는_색상으로는_옵션을_만들_수_없다() {
        long productId = insertProduct(brandA, "CODE-1");
        assertThatThrownBy(() -> insertOption(productId, 999L, SIZE_M, 1))
                .hasMessageContaining("fk_product_options_color");
    }

    @Test
    void 하위_카테고리가_있는_대분류는_삭제할_수_없다() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM categories WHERE id = 1"))
                .hasMessageContaining("fk_categories_parent");
    }

    @Test
    void 주문된_옵션은_물리_삭제할_수_없다() {
        long optionId = insertOption(insertProduct(brandA, "CODE-1"), BLACK, SIZE_M, 5);
        long shipmentId = insertShipment(insertOrder(insertUser("buyer2@test.com", "USER")), sellerA);
        jdbc.update("""
                INSERT INTO order_items (shipment_id, option_id, product_name, color_name, size_label, unit_price, quantity)
                VALUES (?, ?, '상품', '블랙', 'M', 1000, 1)""", shipmentId, optionId);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM product_options WHERE id = ?", optionId))
                .hasMessageContaining("fk_order_items_option");
    }

    // ===== helpers =====

    private long insertUser(String email, String role) {
        jdbc.update("INSERT INTO users (email, password, name, role) VALUES (?, '{noop}pw', '테스터', ?)", email, role);
        return lastId();
    }

    private long insertSeller(long userId, String businessNumber) {
        jdbc.update("""
                INSERT INTO sellers (user_id, business_number, company_name, representative_name)
                VALUES (?, ?, '회사', '대표')""", userId, businessNumber);
        return lastId();
    }

    private long insertProduct(long brandId, String code) {
        jdbc.update("""
                INSERT INTO products (brand_id, category_id, product_code, name, price, gender, season, ship_days)
                VALUES (?, ?, ?, '상품', 1000, 'UNISEX', '24FW', 3)""", brandId, CLOTHING_LEAF, code);
        return lastId();
    }

    private long insertOption(long productId, long colorId, long sizeId, int stock) {
        jdbc.update("INSERT INTO product_options (product_id, color_id, size_id, stock_quantity) VALUES (?, ?, ?, ?)",
                productId, colorId, sizeId, stock);
        return lastId();
    }

    private long insertOrder(long userId) {
        jdbc.update("""
                INSERT INTO orders (user_id, total_price, receiver_name, receiver_phone, zip_code, address)
                VALUES (?, 1000, '수령인', '01012345678', '12345', '서울')""", userId);
        return lastId();
    }

    private long insertShipment(long orderId, long sellerId) {
        jdbc.update("INSERT INTO shipments (order_id, seller_id) VALUES (?, ?)", orderId, sellerId);
        return lastId();
    }

    private long lastId() {
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? -1 : id;
    }
}
