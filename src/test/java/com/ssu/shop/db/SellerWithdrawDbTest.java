package com.ssu.shop.db;

import static com.ssu.shop.support.DbFixtures.BLACK;
import static org.assertj.core.api.Assertions.assertThat;

import com.ssu.shop.domain.cart.dto.CartDtos.CartItemAddRequest;
import com.ssu.shop.domain.cart.service.CartService;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderCreateRequest;
import com.ssu.shop.domain.order.service.OrderService;
import com.ssu.shop.domain.user.service.AdminUserService;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.DbFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 관리자의 판매자 탈퇴 처리를 실제 MySQL 에서 확인한다 (서비스 트랜잭션이 실제로 커밋되는지까지).
 * 실행: DB_TEST_URL 이 있을 때만. 실제 커밋이 필요해 @Transactional 을 쓰지 않고, 전용 이름(withdraw-test-*)으로 만든 것만 지운다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class SellerWithdrawDbTest {

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
    AdminUserService adminUserService;
    @Autowired
    CartService cartService;
    @Autowired
    OrderService orderService;

    DbFixtures db;
    /** 탈퇴하면 이메일이 바뀌어 DbFixtures.cleanUp()(이메일로 찾음)이 못 지우므로 id 로 따로 지운다 */
    final List<Long> withdrawnUserIds = new ArrayList<>();

    long sellerUser;
    long otherProduct;
    List<Long> sellerProducts;

    @BeforeEach
    void setUp() {
        db = new DbFixtures(jdbc, "withdraw-test");
        db.cleanUp();
        sellerUser = db.user("seller", "SELLER");
        long seller = db.seller(sellerUser, "7777777701");
        long brandA = db.brand(seller, "A");
        long brandB = db.brand(seller, "B");
        // 브랜드 순서와 상품 id 순서를 엇갈리게: 먼저 만든 상품이 나중 브랜드(B)에 있다
        long p1 = db.product(brandB, "W-1", 10_000, "ON_SALE");
        long p2 = db.product(brandA, "W-2", 10_000, "PENDING");
        long p3 = db.product(brandB, "W-3", 10_000, "SOLD_OUT");
        sellerProducts = List.of(p1, p2, p3);
        sellerProducts.forEach(p -> db.option(p, BLACK, 5));

        long otherSeller = db.seller(db.user("other", "SELLER"), "7777777702");
        otherProduct = db.product(db.brand(otherSeller, "O"), "W-O", 10_000, "ON_SALE");
        db.option(otherProduct, BLACK, 5);
    }

    @AfterEach
    void tearDown() {
        // 주문·옵션·상품·브랜드는 이름표(브랜드명 WITHDRAW-TEST-*)로 먼저 지우고, 이메일이 바뀐 탈퇴 판매자는 id 로 지운다
        db.cleanUp();
        for (Long userId : withdrawnUserIds) {
            jdbc.update("DELETE FROM sellers WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void 판매자를_탈퇴시키면_계정이_탈퇴되고_그_판매자의_상품만_판매_종료된다() {
        withdrawnUserIds.add(sellerUser);

        adminUserService.withdraw(sellerUser);

        // 커밋된 결과를 JDBC 로 직접 읽는다
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, sellerUser))
                .isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, sellerUser))
                .isEqualTo("withdrawn_" + sellerUser + "@withdrawn.local");
        for (Long productId : sellerProducts) {
            assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, productId))
                    .as("상품 %d", productId).isEqualTo("DELETED");
        }
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, otherProduct))
                .isEqualTo("ON_SALE");
    }

    /**
     * 주문(상품 id 오름차순으로 잠금)과 판매자 탈퇴가 같은 순간에 와도 데드락 없이 하나씩 처리된다.
     * 데이터를 일부러 "브랜드 순서 ≠ 상품 id 순서"로 만든다: 먼저 만든 브랜드(A)에 나중 상품이 들어 있다.
     * 탈퇴가 브랜드 인덱스 순서로 잠그면 주문과 잠그는 순서가 엇갈려 데드락이 날 수 있는 조건이다.
     * 결과는 둘 중 하나여야 한다: 탈퇴가 먼저 끝나 주문이 모두 PRODUCT_NOT_PURCHASABLE, 또는 주문이 먼저 끝나
     * 탈퇴가 HAS_IN_PROGRESS_ORDERS (진행 중인 주문이 있으면 탈퇴 불가) — 어느 쪽이든 데드락은 없어야 한다.
     */
    @Test
    void 주문과_판매자_탈퇴가_동시에_와도_데드락_없이_처리된다() throws Exception {
        int rounds = 10;
        int buyersPerRound = 3;
        for (int round = 0; round < rounds; round++) {
            long user = db.user("race-seller" + round, "SELLER");
            withdrawnUserIds.add(user);
            long seller = db.seller(user, String.format("78%08d", round));
            long brandA = db.brand(seller, "RACE" + round + "-A");
            long brandB = db.brand(seller, "RACE" + round + "-B");
            long first = db.product(brandB, "R" + round + "-1", 10_000, "ON_SALE");  // id 작음, 브랜드 B
            long second = db.product(brandA, "R" + round + "-2", 20_000, "ON_SALE"); // id 큼, 브랜드 A
            long firstOption = db.option(first, BLACK, 100);
            long secondOption = db.option(second, BLACK, 100);

            List<Long> buyers = new ArrayList<>();
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < buyersPerRound; i++) {
                long buyer = db.user("race" + round + "-buyer" + i, "USER");
                cartService.add(buyer, new CartItemAddRequest(secondOption, 1));
                cartService.add(buyer, new CartItemAddRequest(firstOption, 1));
                buyers.add(buyer);
                tasks.add(() -> outcomeOf(() -> orderService.create(buyer,
                        new OrderCreateRequest("구매자", "01012345678", "06978", "서울", null))));
            }
            tasks.add(() -> outcomeOf(() -> adminUserService.withdraw(user)));

            List<String> outcomes = runTogether(tasks);

            // 진행 중인 주문이 있으면 탈퇴할 수 없다 (팀 결정 2026-11-17). 그래서 결과는 둘 중 하나다:
            //  - 탈퇴가 먼저: 탈퇴 OK, 주문은 전부 PRODUCT_NOT_PURCHASABLE
            //  - 주문이 하나라도 먼저: 탈퇴 HAS_IN_PROGRESS_ORDERS(상품 판매 종료까지 롤백), 계정·상품 그대로
            String withdrawOutcome = outcomes.get(buyersPerRound);
            List<String> orderOutcomes = outcomes.subList(0, buyersPerRound);
            long ordered = orderOutcomes.stream().filter("OK"::equals).count();
            assertThat(orderOutcomes).as("주문 (round %d)", round)
                    .isSubsetOf("OK", ErrorCode.PRODUCT_NOT_PURCHASABLE.name());
            if (withdrawOutcome.equals("OK")) {
                assertThat(ordered).as("탈퇴가 먼저면 주문 0건 (round %d)", round).isZero();
                assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, user))
                        .isEqualTo("WITHDRAWN");
                assertThat(jdbc.queryForList("SELECT status FROM products WHERE id IN (?, ?)", String.class,
                        first, second)).containsOnly("DELETED");
            } else {
                assertThat(withdrawOutcome).as("탈퇴 (round %d)", round)
                        .isEqualTo(ErrorCode.HAS_IN_PROGRESS_ORDERS.name());
                assertThat(ordered).as("주문이 먼저면 주문 1건 이상 (round %d)", round).isPositive();
                assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, user))
                        .isEqualTo("ACTIVE");
                assertThat(jdbc.queryForList("SELECT status FROM products WHERE id IN (?, ?)", String.class,
                        first, second)).containsOnly("ON_SALE");
            }
            assertThat(jdbc.queryForObject("SELECT stock_quantity FROM product_options WHERE id = ?", Integer.class,
                    firstOption)).isEqualTo(100 - (int) ordered);
        }
    }

    private static String outcomeOf(Runnable work) {
        try {
            work.run();
            return "OK";
        } catch (BusinessException e) {
            return e.getErrorCode().name();
        } catch (Exception e) {
            return e.getClass().getSimpleName(); // 데드락 등 예상하지 못한 실패
        }
    }

    /** 모든 작업을 같은 순간에 출발시키고 결과를 넣은 순서대로 돌려준다 */
    private static List<String> runTogether(List<Callable<String>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (Callable<String> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        ready.await();
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : futures) {
            outcomes.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return outcomes;
    }
}
