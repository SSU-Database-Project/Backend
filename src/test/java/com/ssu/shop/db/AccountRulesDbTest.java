package com.ssu.shop.db;

import static com.ssu.shop.support.DbFixtures.BLACK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssu.shop.domain.cart.dto.CartDtos.CartItemAddRequest;
import com.ssu.shop.domain.cart.service.CartService;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderCreateRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.PaymentRequest;
import com.ssu.shop.domain.payment.entity.PaymentMethod;
import com.ssu.shop.domain.order.service.OrderService;
import com.ssu.shop.domain.user.dto.AccountDtos.WithdrawalCheckResponse;
import com.ssu.shop.domain.user.entity.UserStatus;
import com.ssu.shop.domain.user.service.AccountService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 회원 정지·탈퇴 규칙, 마이페이지, 동시 로그인 방지를 실제 MySQL 에서 확인한다 (팀 결정 2026-11-17).
 * 실행: DB_TEST_URL 이 있을 때만. 실제 커밋이 필요해 @Transactional 을 쓰지 않고, 전용 이름(account-test-*)으로 만든 것만 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class AccountRulesDbTest {

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

    static final OrderCreateRequest ORDER = new OrderCreateRequest("구매자", "01012345678", "06978", "서울", null,
            new PaymentRequest(PaymentMethod.BANK_TRANSFER, null));

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AdminUserService adminUserService;
    @Autowired
    AccountService accountService;
    @Autowired
    CartService cartService;
    @Autowired
    OrderService orderService;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    DbFixtures db;
    /** 탈퇴하면 이메일이 바뀌어 DbFixtures.cleanUp()(이메일로 찾음)이 못 지우므로 id 로 따로 지운다 */
    final List<Long> withdrawnUserIds = new ArrayList<>();

    long buyer;
    long sellerUser;
    long product;
    long option;

    @BeforeEach
    void setUp() {
        db = new DbFixtures(jdbc, "account-test");
        db.cleanUp();
        buyer = db.user("buyer", "USER");
        sellerUser = db.user("seller", "SELLER");
        long seller = db.seller(sellerUser, "6666666601");
        product = db.product(db.brand(seller, "A"), "AC-1", 10_000, "ON_SALE");
        option = db.option(product, BLACK, 100);
    }

    @AfterEach
    void tearDown() {
        // 탈퇴한 구매자의 주문은 이메일로 못 찾으므로 먼저 지운다
        for (Long userId : withdrawnUserIds) {
            jdbc.update("DELETE r FROM payment_refunds r JOIN payments p ON p.id = r.payment_id "
                    + "JOIN orders o ON o.id = p.order_id WHERE o.user_id = ?", userId);
            jdbc.update("DELETE p FROM payments p JOIN orders o ON o.id = p.order_id WHERE o.user_id = ?", userId);
            jdbc.update("DELETE h FROM stock_history h JOIN order_items i ON i.id = h.order_item_id "
                    + "JOIN shipments s ON s.id = i.shipment_id JOIN orders o ON o.id = s.order_id WHERE o.user_id = ?",
                    userId);
            jdbc.update("DELETE i FROM order_items i JOIN shipments s ON s.id = i.shipment_id "
                    + "JOIN orders o ON o.id = s.order_id WHERE o.user_id = ?", userId);
            jdbc.update("DELETE s FROM shipments s JOIN orders o ON o.id = s.order_id WHERE o.user_id = ?", userId);
            jdbc.update("DELETE FROM orders WHERE user_id = ?", userId);
            jdbc.update("DELETE i FROM cart_items i JOIN carts c ON c.id = i.cart_id WHERE c.user_id = ?", userId);
            jdbc.update("DELETE FROM carts WHERE user_id = ?", userId);
        }
        db.cleanUp();
        for (Long userId : withdrawnUserIds) {
            jdbc.update("DELETE FROM sellers WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    private void order(long buyerId) {
        cartService.add(buyerId, new CartItemAddRequest(option, 1));
        orderService.create(buyerId, ORDER);
    }

    private void setShipmentStatus(long buyerId, String status) {
        jdbc.update("""
                UPDATE shipments s JOIN orders o ON o.id = s.order_id
                   SET s.status = ?, s.confirmed_at = IF(? = 'CONFIRMED', NOW(6), NULL),
                       s.canceled_at = IF(? = 'CANCELED', NOW(6), NULL),
                       s.canceled_by = IF(? = 'CANCELED', 'BUYER', NULL)
                 WHERE o.user_id = ?""", status, status, status, status, buyerId);
    }

    private String userStatus(long userId) {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, userId);
    }

    private static ErrorCode errorOf(Runnable work) {
        try {
            work.run();
            return null;
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
    }

    // ===== 관리자의 정지·탈퇴 =====

    @Test
    void 진행_중인_주문이_있으면_관리자가_구매자와_판매자를_정지_탈퇴시킬_수_없다() {
        order(buyer);

        for (String status : List.of("ORDERED", "PREPARING", "SHIPPING")) {
            setShipmentStatus(buyer, status);
            assertThat(errorOf(() -> adminUserService.changeStatus(buyer, UserStatus.SUSPENDED))).as(status)
                    .isEqualTo(ErrorCode.HAS_IN_PROGRESS_ORDERS);
            assertThat(errorOf(() -> adminUserService.withdraw(buyer))).as(status)
                    .isEqualTo(ErrorCode.HAS_IN_PROGRESS_ORDERS);
            assertThat(errorOf(() -> adminUserService.changeStatus(sellerUser, UserStatus.SUSPENDED))).as(status)
                    .isEqualTo(ErrorCode.HAS_IN_PROGRESS_ORDERS);
            assertThat(errorOf(() -> adminUserService.withdraw(sellerUser))).as(status)
                    .isEqualTo(ErrorCode.HAS_IN_PROGRESS_ORDERS);
        }
        // 거절된 판매자 탈퇴는 상품 판매 종료까지 롤백된다
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product))
                .isEqualTo("ON_SALE");
        assertThat(userStatus(buyer)).isEqualTo("ACTIVE");
        assertThat(userStatus(sellerUser)).isEqualTo("ACTIVE");
    }

    @Test
    void 배송완료_구매확정_취소만_있으면_정지할_수_있고_해제는_언제든_된다() {
        order(buyer);
        for (String status : List.of("DELIVERED", "CONFIRMED", "CANCELED")) {
            setShipmentStatus(buyer, status);
            adminUserService.changeStatus(buyer, UserStatus.SUSPENDED);
            adminUserService.changeStatus(sellerUser, UserStatus.SUSPENDED);
            assertThat(userStatus(buyer)).as(status).isEqualTo("SUSPENDED");
            assertThat(userStatus(sellerUser)).as(status).isEqualTo("SUSPENDED");
            adminUserService.changeStatus(buyer, UserStatus.ACTIVE);
            adminUserService.changeStatus(sellerUser, UserStatus.ACTIVE);
        }
        assertThat(userStatus(buyer)).isEqualTo("ACTIVE");
    }

    @Test
    void 정지된_판매자의_상품은_주문할_수_없고_해제하면_다시_주문된다() {
        adminUserService.changeStatus(sellerUser, UserStatus.SUSPENDED);
        cartService.add(buyer, new CartItemAddRequest(option, 1));

        assertThat(errorOf(() -> orderService.create(buyer, ORDER))).isEqualTo(ErrorCode.PRODUCT_NOT_PURCHASABLE);
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM product_options WHERE id = ?", Integer.class,
                option)).isEqualTo(100);

        adminUserService.changeStatus(sellerUser, UserStatus.ACTIVE);
        orderService.create(buyer, ORDER);
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM product_options WHERE id = ?", Integer.class,
                option)).isEqualTo(99);
    }

    /**
     * 주문과 관리자의 정지가 같은 순간에 와도 "진행 중 주문이 있는 정지된 회원"이 생기지 않는다.
     * 결과는 둘 중 하나: 주문 OK + 정지 HAS_IN_PROGRESS_ORDERS, 또는 정지 OK + 주문 거절. 둘 다 OK 면 규칙 위반이다.
     */
    @Test
    void 주문과_구매자_정지가_동시에_와도_둘_다_성공하지_않는다() throws Exception {
        for (int round = 0; round < 10; round++) {
            long racer = db.user("race-buyer" + round, "USER");
            cartService.add(racer, new CartItemAddRequest(option, 1));

            List<String> outcomes = runTogether(List.of(
                    () -> outcomeOf(() -> orderService.create(racer, ORDER)),
                    () -> outcomeOf(() -> adminUserService.changeStatus(racer, UserStatus.SUSPENDED))));

            assertThat(outcomes).as("round %d", round).isIn(
                    List.of("OK", ErrorCode.HAS_IN_PROGRESS_ORDERS.name()),
                    List.of(ErrorCode.ACCOUNT_SUSPENDED.name(), "OK"));
            long inProgress = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM shipments s JOIN orders o ON o.id = s.order_id
                     WHERE o.user_id = ? AND s.status IN ('ORDERED', 'PREPARING', 'SHIPPING')""", Long.class, racer);
            assertThat(userStatus(racer).equals("SUSPENDED") && inProgress > 0).as("round %d", round).isFalse();
        }
    }

    @Test
    void 주문과_판매자_정지가_동시에_와도_둘_다_성공하지_않는다() throws Exception {
        for (int round = 0; round < 10; round++) {
            long racingSellerUser = db.user("race-seller" + round, "SELLER");
            long racingSeller = db.seller(racingSellerUser, String.format("67%08d", round));
            long racingOption = db.option(db.product(db.brand(racingSeller, "R" + round), "RC-" + round, 10_000,
                    "ON_SALE"), BLACK, 100);
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                long racer = db.user("race" + round + "-buyer" + i, "USER");
                cartService.add(racer, new CartItemAddRequest(racingOption, 1));
                tasks.add(() -> outcomeOf(() -> orderService.create(racer, ORDER)));
            }
            tasks.add(() -> outcomeOf(() -> adminUserService.changeStatus(racingSellerUser, UserStatus.SUSPENDED)));

            List<String> outcomes = runTogether(tasks);

            List<String> orderOutcomes = outcomes.subList(0, 3);
            assertThat(orderOutcomes).isSubsetOf("OK", ErrorCode.PRODUCT_NOT_PURCHASABLE.name());
            long ordered = orderOutcomes.stream().filter("OK"::equals).count();
            if (outcomes.get(3).equals("OK")) {
                assertThat(ordered).as("정지가 먼저면 주문 0건 (round %d)", round).isZero();
            } else {
                assertThat(outcomes.get(3)).isEqualTo(ErrorCode.HAS_IN_PROGRESS_ORDERS.name());
                assertThat(ordered).as("주문이 먼저면 1건 이상 (round %d)", round).isPositive();
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipments WHERE seller_id = ?", Long.class,
                    racingSeller)).isEqualTo(ordered);
        }
    }

    // ===== 본인 탈퇴 =====

    @Test
    void 구매자는_진행_중인_주문이_있어도_확인_후_탈퇴하고_배송은_그대로_남는다() {
        order(buyer);

        WithdrawalCheckResponse check = accountService.withdrawalCheck(buyer);
        assertThat(check.canWithdraw()).isTrue();
        assertThat(check.inProgressOrderCount()).isEqualTo(1);
        assertThat(check.message()).contains("진행 중인 주문이 1건 존재합니다");

        assertThat(errorOf(() -> accountService.withdraw(buyer, "wrong"))).isEqualTo(ErrorCode.PASSWORD_MISMATCH);
        assertThat(userStatus(buyer)).isEqualTo("ACTIVE");

        withdrawnUserIds.add(buyer);
        accountService.withdraw(buyer, "pw");
        assertThat(userStatus(buyer)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("""
                SELECT s.status FROM shipments s JOIN orders o ON o.id = s.order_id WHERE o.user_id = ?""",
                String.class, buyer)).isEqualTo("ORDERED");
    }

    @Test
    void 판매자는_진행_중인_주문이_있으면_본인_탈퇴할_수_없고_끝나면_탈퇴된다() {
        order(buyer);

        WithdrawalCheckResponse check = accountService.withdrawalCheck(sellerUser);
        assertThat(check.canWithdraw()).isFalse();
        assertThat(check.message()).contains("1건 있어 탈퇴할 수 없습니다");
        assertThatThrownBy(() -> accountService.withdraw(sellerUser, "pw"))
                .extracting("errorCode").isEqualTo(ErrorCode.HAS_IN_PROGRESS_ORDERS);
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product))
                .isEqualTo("ON_SALE");

        setShipmentStatus(buyer, "DELIVERED");
        assertThat(accountService.withdrawalCheck(sellerUser).canWithdraw()).isTrue();
        withdrawnUserIds.add(sellerUser);
        accountService.withdraw(sellerUser, "pw");
        assertThat(userStatus(sellerUser)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product))
                .isEqualTo("DELETED");
    }

    // ===== 동시 로그인 방지 + 마이페이지 (HTTP 로 끝까지) =====

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return "Bearer " + json.at("/data/accessToken").asText();
    }

    @Test
    void 다른_곳에서_로그인하면_먼저_로그인한_토큰은_401_LOGGED_IN_ELSEWHERE() throws Exception {
        String email = "account-test-buyer@test.com";
        String first = login(email, "pw");
        String second = login(email, "pw");

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("LOGGED_IN_ELSEWHERE"));
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void 마이페이지에서_정보를_고치고_비밀번호를_바꾸면_다시_로그인해야_한다() throws Exception {
        String sellerEmail = "account-test-seller@test.com";
        String token = login(sellerEmail, "pw");

        mockMvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"새이름","phone":"010-1234-5678",
                                 "seller":{"companyName":"새 상호","representativeName":"새 대표"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.phone").value("01012345678"))
                .andExpect(jsonPath("$.data.seller.companyName").value("새 상호"))
                .andExpect(jsonPath("$.data.seller.representativeName").value("새 대표"))
                .andExpect(jsonPath("$.data.seller.businessNumber").value("6666666601"));

        mockMvc.perform(patch("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"newpass1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_MISMATCH"));
        mockMvc.perform(patch("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"pw\",\"newPassword\":\"newpass1234\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("LOGIN_EXPIRED"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + sellerEmail + "\",\"password\":\"pw\"}"))
                .andExpect(status().isUnauthorized());
        String renewed = login(sellerEmail, "newpass1234");
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, renewed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"));
    }

    @Test
    void 일반회원은_판매자_정보를_보낼_수_없다() throws Exception {
        String token = login("account-test-buyer@test.com", "pw");

        mockMvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"이름\",\"seller\":{\"companyName\":\"a\",\"representativeName\":\"b\"}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"이름\",\"phone\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").doesNotExist());
    }

    // ===== 동시 실행 도우미 (SellerWithdrawDbTest 와 같음) =====

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
