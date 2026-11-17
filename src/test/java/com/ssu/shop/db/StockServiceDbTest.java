package com.ssu.shop.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.ssu.shop.domain.inventory.service.StockService;
import com.ssu.shop.domain.order.entity.OrderItem;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재고 변경 서비스를 실제 MySQL 에서 검증한다: 비관적 락, 동시 차감, 롤백, 재고와 이력의 정합성.
 * 정합성 기준은 팀의 정합성 검사와 같다 —
 *  (01) 옵션 재고 = 마지막 이력(id 가 가장 큰 것)의 quantity_after
 *  (03) 이력이 끊기지 않음: 모든 이력에서 quantity_after = 직전 quantity_after + change_qty
 *  (13) 주문 줄마다 ORDER 이력 1건   (14) 복원한 주문 줄마다 CANCEL 이력 1건
 *
 * 실행: DB_TEST_URL 이 있을 때만. 락과 동시성은 실제 커밋이 있어야 확인되므로 @Transactional 을 쓰지 않고,
 * 만든 데이터는 테스트 앞뒤로 직접 지운다. 다른 DB 테스트(통계 등)와 섞이지 않도록
 * 전용 이름(stock-test-*, STOCK-TEST)을 쓰고 주문 시각은 먼 과거(2000-01-01)로 넣는다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class StockServiceDbTest {

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

    // V4 고정 시드
    static final long CLOTHING_LEAF = 11L; // 상의 > 반소매 티셔츠
    static final long BLACK = 1L;
    static final long WHITE = 2L;
    static final long SIZE_M = 3L;

    static final String SELLER_EMAIL = "stock-test-seller@test.com";
    static final String BUYER_EMAIL = "stock-test-buyer@test.com";
    static final String BRAND = "STOCK-TEST";
    static final String PAST = "2000-01-01 00:00:00";

    @Autowired
    StockService stockService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager em;
    @Autowired
    PlatformTransactionManager txManager;

    long sellerId;
    long buyerId;
    long productId;

    @BeforeEach
    void setUp() {
        cleanUp(); // 이전 실행이 중간에 죽어 남긴 데이터가 있어도 다시 시작할 수 있게
        long sellerUser = insert(
                "INSERT INTO users (email, password, name, role) VALUES (?, '{noop}pw', '재고테스트', 'SELLER')",
                SELLER_EMAIL);
        sellerId = insert("""
                INSERT INTO sellers (user_id, business_number, company_name, representative_name)
                VALUES (?, '9999999990', '재고테스트', '대표')""", sellerUser);
        buyerId = insert(
                "INSERT INTO users (email, password, name, role) VALUES (?, '{noop}pw', '재고테스트', 'USER')",
                BUYER_EMAIL);
        long brandId = insert("INSERT INTO brands (seller_id, name, status) VALUES (?, ?, 'APPROVED')", sellerId,
                BRAND);
        productId = insert("""
                INSERT INTO products (brand_id, category_id, product_code, name, price, gender, season, ship_days)
                VALUES (?, ?, 'STOCK-TEST-1', '재고 테스트 상품', 1000, 'UNISEX', '24FW', 3)""", brandId, CLOTHING_LEAF);
    }

    @AfterEach
    void cleanUp() {
        String ofTestBrand = """
                 JOIN products p ON p.id = o.product_id
                 JOIN brands b ON b.id = p.brand_id
                WHERE b.name = ?""";
        jdbc.update("DELETE h FROM stock_history h JOIN product_options o ON o.id = h.option_id" + ofTestBrand, BRAND);
        jdbc.update("DELETE i FROM order_items i JOIN product_options o ON o.id = i.option_id" + ofTestBrand, BRAND);
        jdbc.update("""
                DELETE s FROM shipments s
                  JOIN orders d ON d.id = s.order_id
                  JOIN users u ON u.id = d.user_id
                 WHERE u.email = ?""", BUYER_EMAIL);
        jdbc.update("DELETE d FROM orders d JOIN users u ON u.id = d.user_id WHERE u.email = ?", BUYER_EMAIL);
        jdbc.update("DELETE o FROM product_options o" + ofTestBrand, BRAND);
        jdbc.update("DELETE p FROM products p JOIN brands b ON b.id = p.brand_id WHERE b.name = ?", BRAND);
        jdbc.update("DELETE FROM brands WHERE name = ?", BRAND);
        jdbc.update("DELETE s FROM sellers s JOIN users u ON u.id = s.user_id WHERE u.email = ?", SELLER_EMAIL);
        jdbc.update("DELETE FROM users WHERE email IN (?, ?)", SELLER_EMAIL, BUYER_EMAIL);
    }

    // ===== 정합성 =====

    @Test
    void 입고_차감_조정_복원_뒤에도_재고는_마지막_이력과_같다() {
        long optionId = option(BLACK, 0);
        OrderItem item = orderItem(optionId, 3);

        stockService.inbound(optionId, 10, "첫 입고");
        stockService.decrease(List.of(item));
        stockService.adjust(optionId, -2, "파손");
        stockService.restore(List.of(item));

        assertThat(stockOf(optionId)).isEqualTo(8);
        assertThat(historyOf(optionId)).extracting("type", "change_qty", "quantity_after").containsExactly(
                tuple("IN", 10, 10),
                tuple("ORDER", -3, 7),
                tuple("ADJUST", -2, 5),
                tuple("CANCEL", 3, 8));
        assertStockMatchesHistory(optionId);
        assertThat(historyCount(item, "ORDER")).isEqualTo(1);
        assertThat(historyCount(item, "CANCEL")).isEqualTo(1);
    }

    // ===== 동시성 =====

    @Test
    void 재고_10개에_동시_차감_20건이면_10건만_성공하고_재고는_0이_된다() throws Exception {
        long optionId = option(BLACK, 10);
        int threads = 20;
        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            items.add(orderItem(optionId, 1));
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();

        for (OrderItem item : items) {
            results.add(pool.submit(() -> {
                ready.countDown();
                start.await(); // 모든 스레드가 준비된 뒤 동시에 출발
                try {
                    stockService.decrease(List.of(item));
                    return "OK";
                } catch (BusinessException e) {
                    return e.getErrorCode().name();
                }
            }));
        }
        ready.await();
        start.countDown();

        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(10);
        assertThat(outcomes).filteredOn(ErrorCode.OUT_OF_STOCK.name()::equals).hasSize(10);
        assertThat(stockOf(optionId)).isZero();
        assertThat(historyOf(optionId)).hasSize(10); // 성공한 주문 줄마다 ORDER 이력 1건, 실패한 줄은 0건
        for (int i = 0; i < threads; i++) {
            assertThat(historyCount(items.get(i), "ORDER")).isEqualTo("OK".equals(outcomes.get(i)) ? 1 : 0);
        }
        assertStockMatchesHistory(optionId);
    }

    @Test
    void 옵션_두_개_중_하나라도_모자라면_둘_다_차감되지_않는다() {
        long enough = option(BLACK, 5);
        long lacking = option(WHITE, 1);
        List<OrderItem> items = List.of(orderItem(enough, 2), orderItem(lacking, 2));

        assertThatThrownBy(() -> stockService.decrease(items))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK));

        // 먼저 처리된 옵션의 차감과 이력도 롤백된다
        assertThat(stockOf(enough)).isEqualTo(5);
        assertThat(stockOf(lacking)).isEqualTo(1);
        assertThat(historyOf(enough)).isEmpty();
        assertThat(historyOf(lacking)).isEmpty();
    }

    @Test
    void 같은_옵션들을_서로_반대_순서로_동시에_주문해도_데드락_없이_모두_성공한다() throws Exception {
        long a = option(BLACK, 100);
        long b = option(WHITE, 100);
        int rounds = 10;
        List<List<OrderItem>> aThenB = new ArrayList<>();
        List<List<OrderItem>> bThenA = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            aThenB.add(List.of(orderItem(a, 1), orderItem(b, 1)));
            bThenA.add(List.of(orderItem(b, 1), orderItem(a, 1)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> results = new ArrayList<>();
        for (List<List<OrderItem>> orders : List.of(aThenB, bThenA)) {
            results.add(pool.submit(() -> {
                start.await();
                for (List<OrderItem> order : orders) {
                    stockService.decrease(order);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<Void> f : results) {
            f.get(60, TimeUnit.SECONDS); // 데드락이면 MySQL 이 한쪽을 실패시켜 여기서 예외가 난다
        }
        pool.shutdown();

        assertThat(stockOf(a)).isEqualTo(80);
        assertThat(stockOf(b)).isEqualTo(80);
        assertStockMatchesHistory(a);
        assertStockMatchesHistory(b);
    }

    // ===== 같은 트랜잭션에서 옵션을 미리 읽어 둔 경우 (주문 서비스가 옵션 정보를 먼저 조회하는 상황) =====

    @Test
    void 미리_읽은_뒤_다른_주문이_재고를_바꿔도_차감은_최신_재고에서_계산한다() {
        long optionId = option(BLACK, 10);
        OrderItem other = orderItem(optionId, 3);
        OrderItem mine = orderItem(optionId, 7);

        inTransaction(() -> {
            OrderItem managed = em.find(OrderItem.class, mine.getId());
            assertThat(managed.getOption().getStockQuantity()).isEqualTo(10); // 옵션을 미리 읽음
            inAnotherTransaction(() -> stockService.decrease(List.of(other))); // 그 사이 다른 주문이 3개 차감 → DB 는 7

            stockService.decrease(List.of(managed)); // 미리 읽은 10 이 아니라 7 에서 7 을 뺀다

            assertThat(managed.getOption().getStockQuantity()).isZero(); // 미리 읽어 둔 객체도 최신 값으로 바뀜
        });

        assertThat(stockOf(optionId)).isZero();
        assertThat(historyOf(optionId)).extracting("type", "change_qty", "quantity_after").containsExactly(
                tuple("ORDER", -3, 7),
                tuple("ORDER", -7, 0));
        assertStockMatchesHistory(optionId);
    }

    @Test
    void 미리_읽은_재고로는_충분해_보여도_최신_재고가_모자라면_OUT_OF_STOCK() {
        long optionId = option(BLACK, 10);
        OrderItem other = orderItem(optionId, 3);
        OrderItem mine = orderItem(optionId, 8);

        assertThatThrownBy(() -> inTransaction(() -> {
            OrderItem managed = em.find(OrderItem.class, mine.getId());
            assertThat(managed.getOption().getStockQuantity()).isEqualTo(10); // 10 ≥ 8 이라 충분해 보인다
            inAnotherTransaction(() -> stockService.decrease(List.of(other))); // DB 는 7

            stockService.decrease(List.of(managed)); // 7 < 8
        })).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK));

        assertThat(stockOf(optionId)).isEqualTo(7);
        assertThat(historyCount(mine, "ORDER")).isZero();
        assertStockMatchesHistory(optionId);
    }

    @Test
    void 미리_읽은_뒤_다른_주문이_재고를_바꿔도_복원은_최신_재고에_더한다() {
        long optionId = option(BLACK, 10);
        OrderItem other = orderItem(optionId, 4);
        OrderItem mine = orderItem(optionId, 3);
        stockService.decrease(List.of(mine)); // 내 주문: 10 → 7

        inTransaction(() -> {
            OrderItem managed = em.find(OrderItem.class, mine.getId());
            assertThat(managed.getOption().getStockQuantity()).isEqualTo(7); // 옵션을 미리 읽음
            inAnotherTransaction(() -> stockService.decrease(List.of(other))); // 그 사이 다른 주문이 4개 차감 → DB 는 3

            stockService.restore(List.of(managed)); // 미리 읽은 7 이 아니라 3 에 3 을 더한다
        });

        assertThat(stockOf(optionId)).isEqualTo(6);
        assertThat(historyOf(optionId)).extracting("type", "change_qty", "quantity_after").containsExactly(
                tuple("ORDER", -3, 7),
                tuple("ORDER", -4, 3),
                tuple("CANCEL", 3, 6));
        assertThat(historyCount(mine, "CANCEL")).isEqualTo(1);
        assertStockMatchesHistory(optionId);
    }

    // ===== helpers =====

    /** (03) 이력이 끊기지 않고 (01) 재고가 마지막 이력과 같은지 확인한다 */
    private void assertStockMatchesHistory(long optionId) {
        List<Map<String, Object>> history = historyOf(optionId);
        assertThat(history).isNotEmpty();
        for (int i = 1; i < history.size(); i++) {
            int before = (Integer) history.get(i - 1).get("quantity_after");
            int change = (Integer) history.get(i).get("change_qty");
            assertThat(history.get(i).get("quantity_after")).as("이력 %d번째의 quantity_after", i + 1)
                    .isEqualTo(before + change);
        }
        assertThat(stockOf(optionId)).isEqualTo(history.get(history.size() - 1).get("quantity_after"));
    }

    private int stockOf(long optionId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM product_options WHERE id = ?", Integer.class, optionId);
    }

    private List<Map<String, Object>> historyOf(long optionId) {
        return jdbc.queryForList(
                "SELECT type, change_qty, quantity_after FROM stock_history WHERE option_id = ? ORDER BY id", optionId);
    }

    private int historyCount(OrderItem item, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM stock_history WHERE order_item_id = ? AND type = ?",
                Integer.class, item.getId(), type);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> work.run());
    }

    /** 다른 스레드(= 다른 DB 연결, 다른 트랜잭션)에서 실행하고 커밋될 때까지 기다린다 */
    private void inAnotherTransaction(Runnable work) {
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            other.submit(work).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            other.shutdown();
        }
    }

    private long option(long colorId, int stock) {
        return insert("INSERT INTO product_options (product_id, color_id, size_id, stock_quantity) VALUES (?, ?, ?, ?)",
                productId, colorId, SIZE_M, stock);
    }

    /** 주문 → 배송 묶음 → 주문 줄을 하나씩 만들고 저장된 주문 줄을 돌려준다 */
    private OrderItem orderItem(long optionId, int quantity) {
        long orderId = insert("""
                INSERT INTO orders (user_id, total_price, receiver_name, receiver_phone, zip_code, address,
                                    created_at, updated_at)
                VALUES (?, 1000, '수령인', '01012345678', '12345', '서울', ?, ?)""", buyerId, PAST, PAST);
        long shipmentId = insert(
                "INSERT INTO shipments (order_id, seller_id, created_at, updated_at) VALUES (?, ?, ?, ?)",
                orderId, sellerId, PAST, PAST);
        long itemId = insert("""
                INSERT INTO order_items (shipment_id, option_id, product_name, color_name, size_label, unit_price, quantity)
                VALUES (?, ?, '재고 테스트 상품', '블랙', 'M', 1000, ?)""", shipmentId, optionId, quantity);
        return em.find(OrderItem.class, itemId);
    }

    /** INSERT 하고 생성된 id 를 돌려준다 (트랜잭션 밖이라 LAST_INSERT_ID() 를 따로 조회하면 다른 연결일 수 있다) */
    private long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }
}
