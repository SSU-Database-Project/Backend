package com.readmate.db;

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
 * DB 제약(UNIQUE / 복합 FK / CHECK / RESTRICT)이 실제 MySQL 에서 잘못된 데이터를 거부하는지 검증한다.
 * 애플리케이션 코드를 거치지 않고 SQL 을 직접 실행해 "DB 가 최후 방어선"인지 확인하는 것이 목적이다.
 * 컨텍스트가 뜨는 것 자체로 Flyway 마이그레이션 + JPA 엔티티-테이블 일치(ddl-auto=validate)도 검증된다.
 *
 * 실행: DB_TEST_URL 환경변수가 있을 때만 (예: jdbc:mysql://localhost:3307/shop_test?allowPublicKeyRetrieval=true&useSSL=false)
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
        // 시연 데이터(db/demo)는 제외하고 순수 스키마 + 기본 시드만으로 검증
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    private static String envOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }

    @Autowired
    JdbcTemplate jdbc;

    long sellerA;
    long sellerB;
    long brandOfB;
    static final long LEAF_CATEGORY = 11L; // V2 시드: 패션의류 > 상의

    @BeforeEach
    void setUp() {
        long userA = insertUser("seller-a@test.com");
        long userB = insertUser("seller-b@test.com");
        sellerA = insertSeller(userA, "1111111111");
        sellerB = insertSeller(userB, "2222222222");
        jdbc.update("INSERT INTO brands (seller_id, name, status) VALUES (?, 'B브랜드', 'APPROVED')", sellerB);
        brandOfB = lastId();
    }

    // ===== UNIQUE =====

    @Test
    void 이메일은_대소문자만_달라도_중복으로_거부된다() {
        insertUser("dup@test.com");
        assertThatThrownBy(() -> insertUser("DUP@Test.com"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_users_email");
    }

    @Test
    void 한_회원은_판매자_정보를_하나만_가질_수_있다() {
        long userId = insertUser("one@test.com");
        insertSeller(userId, "3333333333");
        assertThatThrownBy(() -> insertSeller(userId, "4444444444"))
                .hasMessageContaining("uk_sellers_user");
    }

    @Test
    void 같은_주문에_같은_상품_라인은_하나만() {
        long productId = insertProduct(sellerA, null);
        long userId = insertUser("buyer@test.com");
        long orderId = insertOrder(userId);
        insertOrderItem(orderId, productId, sellerA);
        assertThatThrownBy(() -> insertOrderItem(orderId, productId, sellerA))
                .hasMessageContaining("uk_order_items_order_product");
    }

    // ===== CHECK =====

    @Test
    void 사업자번호는_숫자_10자리만_허용된다() {
        long userId = insertUser("bn@test.com");
        assertThatThrownBy(() -> insertSeller(userId, "123-45-678"))
                .hasMessageContaining("ck_sellers_business_number");
    }

    @Test
    void 재고는_음수가_될_수_없다_동시성의_최후_방어선() {
        long productId = insertProduct(sellerA, null);
        jdbc.update("INSERT INTO inventory (product_id, quantity) VALUES (?, 1)", productId);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE inventory SET quantity = quantity - 2 WHERE product_id = ?", productId))
                .hasMessageContaining("ck_inventory_quantity");
    }

    @Test
    void 조건부_UPDATE는_재고가_부족하면_영향행이_0이다() {
        long productId = insertProduct(sellerA, null);
        jdbc.update("INSERT INTO inventory (product_id, quantity) VALUES (?, 1)", productId);

        int updated = jdbc.update(
                "UPDATE inventory SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?", 2, productId, 2);

        assertThat(updated).isZero();
    }

    @Test
    void 입고_이력은_양수여야_한다() {
        long productId = insertProduct(sellerA, null);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stock_history (product_id, change_qty, quantity_after, type) VALUES (?, -5, 0, 'IN')",
                productId))
                .hasMessageContaining("ck_stock_history_sign");
    }

    @Test
    void 주문_이력은_원인_주문라인이_있어야_한다() {
        long productId = insertProduct(sellerA, null);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stock_history (product_id, change_qty, quantity_after, type) VALUES (?, -1, 0, 'ORDER')",
                productId))
                .hasMessageContaining("ck_stock_history_order_ref");
    }

    @Test
    void 취소_상태와_취소시각은_항상_함께_존재한다() {
        long orderId = insertOrder(insertUser("cancel@test.com"));
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET status = 'CANCELED' WHERE id = ?", orderId))
                .hasMessageContaining("ck_orders_canceled_at");
        assertThatCode(() -> jdbc.update(
                "UPDATE orders SET status = 'CANCELED', canceled_at = NOW(6) WHERE id = ?", orderId))
                .doesNotThrowAnyException();
    }

    // ===== 복합 FK: 브랜드 소유자 = 상품 소유자 =====

    @Test
    void 다른_판매자의_브랜드를_상품에_지정하면_FK_위반() {
        assertThatThrownBy(() -> insertProduct(sellerA, brandOfB))
                .hasMessageContaining("fk_products_brand_seller");
    }

    @Test
    void 본인_브랜드_상품과_브랜드_없는_상품은_허용된다() {
        assertThatCode(() -> insertProduct(sellerB, brandOfB)).doesNotThrowAnyException();
        assertThatCode(() -> insertProduct(sellerA, null)).doesNotThrowAnyException(); // brand_id NULL → FK 검사 생략
    }

    // ===== FK RESTRICT =====

    @Test
    void 하위_카테고리가_있는_대분류는_삭제할_수_없다() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM categories WHERE id = 1"))
                .hasMessageContaining("fk_categories_parent");
    }

    @Test
    void 주문된_상품은_물리_삭제할_수_없다() {
        long productId = insertProduct(sellerA, null);
        insertOrderItem(insertOrder(insertUser("buyer2@test.com")), productId, sellerA);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id = ?", productId))
                .hasMessageContaining("fk_order_items_product");
    }

    // ===== helpers =====

    private long insertUser(String email) {
        jdbc.update("INSERT INTO users (email, password, name) VALUES (?, '{noop}pw', '테스터')", email);
        return lastId();
    }

    private long insertSeller(long userId, String businessNumber) {
        jdbc.update("""
                INSERT INTO sellers (user_id, business_number, company_name, representative_name, status)
                VALUES (?, ?, '회사', '대표', 'APPROVED')""", userId, businessNumber);
        return lastId();
    }

    private long insertProduct(long sellerId, Long brandId) {
        jdbc.update("INSERT INTO products (seller_id, brand_id, category_id, name, price) VALUES (?, ?, ?, '상품', 1000)",
                sellerId, brandId, LEAF_CATEGORY);
        return lastId();
    }

    private long insertOrder(long userId) {
        jdbc.update("""
                INSERT INTO orders (user_id, total_price, receiver_name, receiver_phone, zip_code, address)
                VALUES (?, 1000, '수령인', '01012345678', '12345', '서울')""", userId);
        return lastId();
    }

    private void insertOrderItem(long orderId, long productId, long sellerId) {
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, seller_id, product_name, unit_price, quantity)
                VALUES (?, ?, ?, '상품', 1000, 1)""", orderId, productId, sellerId);
    }

    private long lastId() {
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? -1 : id;
    }
}
