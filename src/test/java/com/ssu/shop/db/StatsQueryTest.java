package com.ssu.shop.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssu.shop.domain.stats.dto.StatsDtos.SalesPoint;
import com.ssu.shop.domain.stats.dto.StatsDtos.TopProduct;
import com.ssu.shop.domain.stats.dto.StatsPeriod;
import com.ssu.shop.domain.stats.repository.StatsRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통계 SQL 을 실제 MySQL 에서 검증한다 (DATE / WEEKDAY / DATE_FORMAT 은 DBMS 함수라 단위 테스트로는 확인할 수 없다).
 * 실행: DB_TEST_URL 이 있을 때만. 각 테스트는 롤백된다.
 *
 * 데이터 (판매자 A 기준, 2026-11 / 2026-10)
 *  11-02(월) 10:00  티셔츠 블랙 2개 × 10,000 + 티셔츠 화이트 1개 × 10,000   → 3개 30,000
 *  11-04(수) 23:59  후드 1개 × 50,000                                   → 1개 50,000 (자정 직전 경계)
 *  11-05(목) 00:00  후드 1개 × 50,000 — 취소                              → 제외
 *  11-09(월) 09:00  티셔츠 블랙 1개 × 10,000                              → 1개 10,000 (다음 주)
 *  10-30(금) 12:00  후드 2개 × 50,000                                   → 2개 100,000 (지난달)
 *  판매자 B: 11-02 다른 상품 5개 → A 통계에서 제외, 플랫폼 통계에는 포함
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class StatsQueryTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DB_TEST_URL"));
        registry.add("spring.datasource.username", () -> envOr("DB_TEST_USERNAME", "shop"));
        registry.add("spring.datasource.password", () -> envOr("DB_TEST_PASSWORD", "shop1234"));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    private static String envOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StatsRepository stats;

    long sellerA;
    long sellerB;
    long buyer;
    long teeBlack;
    long teeWhite;
    long hood;
    long otherOption;
    long teeProduct;
    long hoodProduct;

    @BeforeEach
    void setUp() {
        sellerA = seller("stat-a@test.com", "5555555551");
        sellerB = seller("stat-b@test.com", "5555555552");
        buyer = user("stat-buyer@test.com", "USER");
        long brandA = brand(sellerA, "STAT-A");
        long brandB = brand(sellerB, "STAT-B");
        teeProduct = product(brandA, "TEE", 10_000);
        hoodProduct = product(brandA, "HOOD", 50_000);
        long other = product(brandB, "OTHER", 7_000);
        teeBlack = option(teeProduct, 1);
        teeWhite = option(teeProduct, 2);
        hood = option(hoodProduct, 1);
        otherOption = option(other, 1);

        order("2026-11-02 10:00:00", sellerA, "CONFIRMED", line(teeBlack, 10_000, 2), line(teeWhite, 10_000, 1));
        order("2026-11-04 23:59:59.999", sellerA, "SHIPPING", line(hood, 50_000, 1));
        order("2026-11-05 00:00:00", sellerA, "CANCELED", line(hood, 50_000, 1));
        order("2026-11-09 09:00:00", sellerA, "ORDERED", line(teeBlack, 10_000, 1));
        order("2026-10-30 12:00:00", sellerA, "CONFIRMED", line(hood, 50_000, 2));
        order("2026-11-02 11:00:00", sellerB, "CONFIRMED", line(otherOption, 7_000, 5));
    }

    @Test
    void 일별_판매는_취소와_다른_판매자를_빼고_날짜별로_묶는다() {
        List<SalesPoint> points = stats.sellerSales(sellerA, StatsPeriod.DAILY,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));

        assertThat(points).containsExactly(
                new SalesPoint(LocalDate.of(2026, 11, 2), 3, 30_000, 1),
                new SalesPoint(LocalDate.of(2026, 11, 4), 1, 50_000, 1),   // 23:59:59.999 는 4일
                new SalesPoint(LocalDate.of(2026, 11, 9), 1, 10_000, 1));  // 5일 0시 주문은 취소라 없음
    }

    @Test
    void 기간_끝날은_자정_직전까지_포함하고_다음날_0시는_뺀다() {
        List<SalesPoint> points = stats.sellerSales(sellerA, StatsPeriod.DAILY,
                LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4));
        assertThat(points).extracting(SalesPoint::quantity).containsExactly(1L);
    }

    @Test
    void 주별은_월요일_시작으로_묶는다() {
        List<SalesPoint> points = stats.sellerSales(sellerA, StatsPeriod.WEEKLY,
                LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 15));

        assertThat(points).containsExactly(
                new SalesPoint(LocalDate.of(2026, 10, 26), 2, 100_000, 1),  // 10/30(금)
                new SalesPoint(LocalDate.of(2026, 11, 2), 4, 80_000, 2),    // 11/2, 11/4
                new SalesPoint(LocalDate.of(2026, 11, 9), 1, 10_000, 1));
    }

    @Test
    void 월별은_1일_시작으로_묶는다() {
        List<SalesPoint> points = stats.sellerSales(sellerA, StatsPeriod.MONTHLY,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30));

        assertThat(points).containsExactly(
                new SalesPoint(LocalDate.of(2026, 10, 1), 2, 100_000, 1),
                new SalesPoint(LocalDate.of(2026, 11, 1), 5, 90_000, 3));
    }

    @Test
    void 플랫폼_통계는_모든_판매자를_합친다() {
        List<SalesPoint> points = stats.platformSales(StatsPeriod.DAILY,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2));
        assertThat(points).containsExactly(new SalesPoint(LocalDate.of(2026, 11, 2), 3 + 5, 30_000 + 35_000, 2));
    }

    @Test
    void 상품_순위는_옵션을_상품으로_합치고_판매량순() {
        List<TopProduct> top = stats.sellerTopProducts(sellerA, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 30), false, 10);

        // 티셔츠: 블랙 2 + 화이트 1 + 블랙 1 = 4개 40,000 / 후드: 1 + 2 = 3개 150,000 (취소 1개 제외)
        assertThat(top).extracting(TopProduct::productId).containsExactly(teeProduct, hoodProduct);
        assertThat(top).extracting(TopProduct::quantity).containsExactly(4L, 3L);

        List<TopProduct> byRevenue = stats.sellerTopProducts(sellerA, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 30), true, 10);
        assertThat(byRevenue).extracting(TopProduct::productId).containsExactly(hoodProduct, teeProduct);
        assertThat(byRevenue).extracting(TopProduct::rank).containsExactly(1, 2);
    }

    @Test
    void 처리할_배송은_주문완료_배송준비_배송중만_센다() {
        assertThat(stats.sellerOpenShipments(sellerA))
                .containsEntry("ORDERED", 1L)
                .containsEntry("SHIPPING", 1L)
                .doesNotContainKey("CANCELED")
                .doesNotContainKey("CONFIRMED");
    }

    // ===== helpers =====

    record Line(long optionId, int price, int qty) {
    }

    private Line line(long optionId, int price, int qty) {
        return new Line(optionId, price, qty);
    }

    private void order(String at, long sellerId, String status, Line... lines) {
        int total = 0;
        for (Line l : lines) {
            total += l.price() * l.qty();
        }
        jdbc.update("""
                INSERT INTO orders (user_id, total_price, receiver_name, receiver_phone, zip_code, address, created_at)
                VALUES (?, ?, '수령인', '01012345678', '12345', '서울', ?)""", buyer, total, at);
        long orderId = lastId();
        jdbc.update("""
                INSERT INTO shipments (order_id, seller_id, status, confirmed_at, canceled_at, created_at)
                VALUES (?, ?, ?, IF(? = 'CONFIRMED', ?, NULL), IF(? = 'CANCELED', ?, NULL), ?)""",
                orderId, sellerId, status, status, at, status, at, at);
        long shipmentId = lastId();
        for (Line l : lines) {
            jdbc.update("""
                    INSERT INTO order_items (shipment_id, option_id, product_name, color_name, size_label, unit_price, quantity)
                    VALUES (?, ?, '상품', '블랙', 'M', ?, ?)""", shipmentId, l.optionId(), l.price(), l.qty());
        }
    }

    private long user(String email, String role) {
        jdbc.update("INSERT INTO users (email, password, name, role) VALUES (?, '{noop}pw', '테스터', ?)", email, role);
        return lastId();
    }

    private long seller(String email, String businessNumber) {
        long userId = user(email, "SELLER");
        jdbc.update("INSERT INTO sellers (user_id, business_number, company_name, representative_name) VALUES (?, ?, '회사', '대표')",
                userId, businessNumber);
        return lastId();
    }

    private long brand(long sellerId, String name) {
        jdbc.update("INSERT INTO brands (seller_id, name, status) VALUES (?, ?, 'APPROVED')", sellerId, name);
        return lastId();
    }

    private long product(long brandId, String code, int price) {
        jdbc.update("""
                INSERT INTO products (brand_id, category_id, product_code, name, price, gender, season, ship_days, status)
                VALUES (?, 11, ?, ?, ?, 'UNISEX', '24FW', 3, 'ON_SALE')""", brandId, code, code, price);
        return lastId();
    }

    private long option(long productId, long colorId) {
        jdbc.update("INSERT INTO product_options (product_id, color_id, size_id, stock_quantity) VALUES (?, ?, 3, 100)",
                productId, colorId);
        return lastId();
    }

    private long lastId() {
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? -1 : id;
    }
}
