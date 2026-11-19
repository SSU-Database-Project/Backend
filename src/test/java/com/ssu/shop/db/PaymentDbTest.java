package com.ssu.shop.db;

import static com.ssu.shop.support.DbFixtures.BLACK;
import static com.ssu.shop.support.DbFixtures.WHITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssu.shop.domain.cart.dto.CartDtos.CartItemAddRequest;
import com.ssu.shop.domain.cart.service.CartService;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderCreateRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderResponse;
import com.ssu.shop.domain.order.dto.OrderDtos.PaymentRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.PaymentResponse;
import com.ssu.shop.domain.order.dto.OrderDtos.ShipmentResponse;
import com.ssu.shop.domain.order.service.OrderService;
import com.ssu.shop.domain.payment.entity.PaymentMethod;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.DbFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * 모의 결제(V7)를 실제 MySQL 에서 확인한다: 승인·기록, 거절 시 전체 롤백, 판매자별 부분 환불, 정합성 규칙.
 * 실행: DB_TEST_URL 이 있을 때만. 실제 커밋이 필요해 @Transactional 을 쓰지 않고, 전용 이름(payment-test-*)만 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class PaymentDbTest {

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
    CartService cartService;
    @Autowired
    OrderService orderService;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    DbFixtures db;
    long buyer;
    long teeOption;   // 판매자 A, 25,000원
    long capOption;   // 판매자 B, 18,000원
    long coatOption;  // 판매자 A, 500,000원 (한도 시연)

    @BeforeEach
    void setUp() {
        db = new DbFixtures(jdbc, "payment-test");
        db.cleanUp();
        buyer = db.user("buyer", "USER");
        long sellerA = db.seller(db.user("seller-a", "SELLER"), "5555555501");
        long sellerB = db.seller(db.user("seller-b", "SELLER"), "5555555502");
        long brandA = db.brand(sellerA, "A");
        teeOption = db.option(db.product(brandA, "PT-1", 25_000, "ON_SALE"), BLACK, 10);
        coatOption = db.option(db.product(brandA, "PT-3", 500_000, "ON_SALE"), WHITE, 10);
        capOption = db.option(db.product(db.brand(sellerB, "B"), "PT-2", 18_000, "ON_SALE"), BLACK, 10);
    }

    @AfterEach
    void tearDown() {
        db.cleanUp();
    }

    private static OrderCreateRequest order(PaymentMethod method, String cardNumber) {
        return new OrderCreateRequest("구매자", "01012345678", "06978", "서울", null, new PaymentRequest(method, cardNumber));
    }

    private int stock(long optionId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM product_options WHERE id = ?", Integer.class, optionId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static ErrorCode errorOf(Runnable work) {
        try {
            work.run();
            return null;
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
    }

    @Test
    void 카드로_결제하면_주문_총액으로_승인하고_카드번호는_끝_4자리만_저장한다() {
        cartService.add(buyer, new CartItemAddRequest(teeOption, 2));
        cartService.add(buyer, new CartItemAddRequest(capOption, 1));

        OrderResponse order = orderService.create(buyer, order(PaymentMethod.CARD, "1234-5678-9012-3456"));

        PaymentResponse payment = order.payment();
        assertThat(payment.method()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.amount()).isEqualTo(68_000).isEqualTo(order.totalPrice());
        assertThat(payment.cardNumberMasked()).isEqualTo("****-****-****-3456");
        assertThat(payment.approvalNo()).startsWith("MOCK-");
        assertThat(payment.paidAt()).isEqualTo(order.createdAt());
        assertThat(payment.balance()).isEqualTo(68_000);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT method, amount, card_last4 FROM payments WHERE order_id = ?", order.orderId());
        assertThat(row).containsEntry("method", "CARD").containsEntry("amount", 68_000L).containsEntry("card_last4", "3456");
    }

    @Test
    void 계좌이체와_간편결제는_카드_정보_없이_승인된다() {
        cartService.add(buyer, new CartItemAddRequest(teeOption, 1));
        OrderResponse first = orderService.create(buyer, order(PaymentMethod.BANK_TRANSFER, null));
        cartService.add(buyer, new CartItemAddRequest(capOption, 1));
        OrderResponse second = orderService.create(buyer, order(PaymentMethod.EASY_PAY, null));

        assertThat(first.payment().method()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(first.payment().cardNumberMasked()).isNull();
        assertThat(second.payment().method()).isEqualTo(PaymentMethod.EASY_PAY);
        assertThat(count("SELECT COUNT(*) FROM payments WHERE order_id IN (?, ?) AND card_last4 IS NULL",
                first.orderId(), second.orderId())).isEqualTo(2);
    }

    @Test
    void 카드사가_거절하면_주문_재고_장바구니가_모두_그대로다() {
        cartService.add(buyer, new CartItemAddRequest(teeOption, 2));

        assertThat(errorOf(() -> orderService.create(buyer, order(PaymentMethod.CARD, "1234-5678-9012-0000"))))
                .isEqualTo(ErrorCode.PAYMENT_DECLINED);

        assertThat(count("SELECT COUNT(*) FROM orders WHERE user_id = ?", buyer)).isZero();
        assertThat(stock(teeOption)).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM cart_items i JOIN carts c ON c.id = i.cart_id WHERE c.user_id = ?",
                buyer)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM stock_history WHERE option_id = ?", teeOption)).isZero();
    }

    @Test
    void 백만원까지는_승인되고_넘으면_한도_초과로_전체_롤백된다() {
        cartService.add(buyer, new CartItemAddRequest(coatOption, 2)); // 1,000,000원
        OrderResponse ok = orderService.create(buyer, order(PaymentMethod.EASY_PAY, null));
        assertThat(ok.payment().amount()).isEqualTo(1_000_000);

        cartService.add(buyer, new CartItemAddRequest(coatOption, 2));
        cartService.add(buyer, new CartItemAddRequest(teeOption, 1));    // 1,025,000원
        assertThatThrownBy(() -> orderService.create(buyer, order(PaymentMethod.BANK_TRANSFER, null)))
                .hasMessageContaining("한도")
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_DECLINED);
        assertThat(count("SELECT COUNT(*) FROM orders WHERE user_id = ?", buyer)).isEqualTo(1);
        assertThat(stock(coatOption)).isEqualTo(8);
        assertThat(stock(teeOption)).isEqualTo(10);
    }

    @Test
    void 판매자별로_취소하면_그_묶음_금액만큼_부분_환불되고_다시_취소해도_환불은_한_번이다() {
        cartService.add(buyer, new CartItemAddRequest(teeOption, 2));  // A: 50,000
        cartService.add(buyer, new CartItemAddRequest(capOption, 1));  // B: 18,000
        OrderResponse order = orderService.create(buyer, order(PaymentMethod.CARD, "1234567890123456"));
        ShipmentResponse capShipment = order.shipments().stream().filter(s -> s.subtotal() == 18_000).findFirst()
                .orElseThrow();

        OrderResponse canceled = orderService.cancel(buyer, order.orderId(), capShipment.shipmentId());

        PaymentResponse payment = canceled.payment();
        assertThat(payment.refundedAmount()).isEqualTo(18_000);
        assertThat(payment.balance()).isEqualTo(50_000);
        assertThat(payment.refunds()).singleElement().satisfies(r -> {
            assertThat(r.shipmentId()).isEqualTo(capShipment.shipmentId());
            assertThat(r.amount()).isEqualTo(18_000);
        });
        assertThat(errorOf(() -> orderService.cancel(buyer, order.orderId(), capShipment.shipmentId())))
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
        assertThat(count("SELECT COUNT(*) FROM payment_refunds WHERE shipment_id = ?", capShipment.shipmentId()))
                .isEqualTo(1);
        // 목록 조회에도 결제·환불이 함께 나온다
        assertThat(orderService.myOrders(buyer, 0, 10).content().get(0).payment().balance()).isEqualTo(50_000);
    }

    @Test
    void 같은_묶음을_동시에_두_번_취소해도_환불은_1건이다() throws Exception {
        cartService.add(buyer, new CartItemAddRequest(capOption, 1));
        OrderResponse order = orderService.create(buyer, order(PaymentMethod.EASY_PAY, null));
        long shipmentId = order.shipments().get(0).shipmentId();

        List<String> outcomes = runTogether(List.of(
                () -> outcomeOf(() -> orderService.cancel(buyer, order.orderId(), shipmentId)),
                () -> outcomeOf(() -> orderService.cancel(buyer, order.orderId(), shipmentId))));

        assertThat(outcomes).containsExactlyInAnyOrder("OK", ErrorCode.INVALID_ORDER_STATUS.name());
        assertThat(count("SELECT COUNT(*) FROM payment_refunds WHERE shipment_id = ?", shipmentId)).isEqualTo(1);
        assertThat(stock(capOption)).isEqualTo(10);
    }

    @Test
    void 결제_정합성_규칙_16_21이_모두_0건이다() throws Exception {
        cartService.add(buyer, new CartItemAddRequest(teeOption, 1));
        cartService.add(buyer, new CartItemAddRequest(capOption, 2));
        OrderResponse order = orderService.create(buyer, order(PaymentMethod.CARD, "1111-2222-3333-4444"));
        orderService.cancel(buyer, order.orderId(), order.shipments().get(0).shipmentId());

        String script = Files.readString(Path.of("scripts/check-integrity.sql"));
        String query = script.substring(script.indexOf("SELECT '01"), script.lastIndexOf(';'));
        List<Map<String, Object>> rules = jdbc.queryForList(query);
        assertThat(rules).hasSize(21);
        assertThat(rules.subList(15, 21)).allSatisfy(r ->
                assertThat(((Number) r.get("bad")).longValue()).as(r.get("rule_name").toString()).isZero());
    }

    @Test
    void 결제_정보가_없거나_형식이_틀리면_400() throws Exception {
        String token = login();
        String noPayment = """
                {"receiverName":"구매자","receiverPhone":"01012345678","zipCode":"06978","address":"서울"}""";
        String cardWithoutNumber = """
                {"receiverName":"구매자","receiverPhone":"01012345678","zipCode":"06978","address":"서울",
                 "payment":{"method":"CARD"}}""";
        for (String body : List.of(noPayment, cardWithoutNumber)) {
            mockMvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void 거절은_HTTP_402_PAYMENT_DECLINED() throws Exception {
        cartService.add(buyer, new CartItemAddRequest(teeOption, 1));
        mockMvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverName":"구매자","receiverPhone":"01012345678","zipCode":"06978","address":"서울",
                                 "payment":{"method":"CARD","cardNumber":"1234-5678-9012-0000"}}"""))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_DECLINED"));
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"payment-test-buyer@test.com\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    private static String outcomeOf(Runnable work) {
        try {
            work.run();
            return "OK";
        } catch (BusinessException e) {
            return e.getErrorCode().name();
        } catch (Exception e) {
            return e.getClass().getSimpleName();
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
