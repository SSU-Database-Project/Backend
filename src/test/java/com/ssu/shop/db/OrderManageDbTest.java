package com.ssu.shop.db;

import static com.ssu.shop.support.DbFixtures.BLACK;
import static com.ssu.shop.support.DbFixtures.WHITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssu.shop.domain.cart.dto.CartDtos.CartItemAddRequest;
import com.ssu.shop.domain.cart.service.CartService;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderResponse;
import com.ssu.shop.domain.order.dto.OrderDtos.SellerShipmentResponse;
import com.ssu.shop.domain.order.dto.OrderDtos.ShipmentResponse;
import com.ssu.shop.domain.order.entity.ShipmentStatus;
import com.ssu.shop.domain.order.service.OrderService;
import com.ssu.shop.domain.order.service.SellerShipmentService;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.DbFixtures;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 주문 조회·취소·구매확정과 판매자 배송 관리를 실제 MySQL 에서 검증한다.
 * 상태 변경이 조건부 UPDATE(WHERE status = 이전 상태)라서 동시 요청 중 하나만 성공하는지가 핵심이다.
 * 정합성 기준은 팀의 정합성 검사와 같다 —
 *  (01·03) 재고 = 마지막 이력, 이력이 끊기지 않음   (13) 주문 줄마다 ORDER 이력 1건
 *  (14) 취소된 배송 묶음의 줄은 CANCEL 이력 1건, 아니면 0건   (15) 취소·확정 시각은 주문 이후
 *
 * 실행: DB_TEST_URL 이 있을 때만. 실제 커밋이 필요해 @Transactional 을 쓰지 않고 전용 이름(manage-test-*)으로 만든
 * 데이터를 앞뒤로 지운다. 주문은 실제 서비스(장바구니 담기 → 주문)로 만들고, 시계는 2000-01-01 로 고정한다.
 */
@SpringBootTest
@Import(OrderCreateDbTest.FixedClock.class)
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class OrderManageDbTest {

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
    OrderService orderService;
    @Autowired
    SellerShipmentService sellerShipmentService;
    @Autowired
    CartService cartService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManagerFactory emf;

    DbFixtures db;

    long sellerUserA;
    long sellerUserB;
    long buyer;
    long otherBuyer;
    long teeBlack;  // 판매자 A, 10,000원, 재고 100
    long teeWhite;  // 판매자 A, 10,000원, 재고 100
    long capBlack;  // 판매자 B, 19,000원, 재고 100

    @BeforeEach
    void setUp() {
        db = new DbFixtures(jdbc, "manage-test");
        db.cleanUp();
        sellerUserA = db.user("seller-a", "SELLER");
        sellerUserB = db.user("seller-b", "SELLER");
        long tee = db.product(db.brand(db.seller(sellerUserA, "6660000001"), "A"), "TEE", 10_000, "ON_SALE");
        teeBlack = db.option(tee, BLACK, 100);
        teeWhite = db.option(tee, WHITE, 100);
        capBlack = db.option(db.product(db.brand(db.seller(sellerUserB, "6660000002"), "B"), "CAP", 19_000, "ON_SALE"),
                BLACK, 100);
        buyer = db.user("buyer", "USER");
        otherBuyer = db.user("buyer2", "USER");
    }

    @AfterEach
    void cleanUp() {
        db.cleanUp();
    }

    // ===== 동시성 =====

    @Test
    void 같은_배송_묶음을_동시에_두_번_취소해도_한_번만_성공하고_재고는_한_번만_복원된다() throws Exception {
        OrderResponse order = order(buyer, teeBlack, 3, teeWhite, 2);
        long shipment = order.shipments().get(0).shipmentId();

        List<String> outcomes = runTogether(List.of(
                () -> outcome(() -> orderService.cancel(buyer, order.orderId(), shipment)),
                () -> outcome(() -> orderService.cancel(buyer, order.orderId(), shipment))));

        assertThat(outcomes).containsExactlyInAnyOrder("OK", ErrorCode.INVALID_ORDER_STATUS.name());
        assertThat(stockOf(teeBlack)).isEqualTo(100); // 97 + 3, 두 번 복원됐다면 103
        assertThat(stockOf(teeWhite)).isEqualTo(100);
        assertThat(statusOf(shipment)).isEqualTo("CANCELED");
        assertIntegrity();
    }

    @Test
    void 구매자_취소와_판매자_배송준비가_동시에_오면_하나만_성공한다() throws Exception {
        int cancelWon = 0;
        int rounds = 8; // 어느 쪽이 이기든 규칙이 지켜지는지 여러 번 확인
        for (int i = 0; i < rounds; i++) {
            OrderResponse order = order(buyer, teeBlack, 1);
            long shipment = order.shipments().get(0).shipmentId();
            int stockBefore = stockOf(teeBlack);

            List<String> outcomes = runTogether(List.of(
                    () -> outcome(() -> orderService.cancel(buyer, order.orderId(), shipment)),
                    () -> outcome(() -> sellerShipmentService.changeStatus(sellerUserA, shipment,
                            ShipmentStatus.PREPARING))));

            assertThat(outcomes).containsExactlyInAnyOrder("OK", ErrorCode.INVALID_ORDER_STATUS.name());
            if ("OK".equals(outcomes.get(0))) { // 취소가 이김 → 재고 복원, 배송준비는 실패
                cancelWon++;
                assertThat(statusOf(shipment)).isEqualTo("CANCELED");
                assertThat(stockOf(teeBlack)).isEqualTo(stockBefore + 1);
            } else {                            // 배송준비가 이김 → 취소 실패, 재고 그대로
                assertThat(statusOf(shipment)).isEqualTo("PREPARING");
                assertThat(stockOf(teeBlack)).isEqualTo(stockBefore);
            }
        }
        System.out.println("취소가 이긴 횟수: " + cancelWon + " / " + rounds);
        assertIntegrity();
    }

    // ===== 상태 규칙 =====

    @Test
    void 배송_상태는_한_칸씩만_앞으로_가고_구매확정은_배송완료에서만_된다() {
        OrderResponse order = order(buyer, teeBlack, 1);
        long shipment = order.shipments().get(0).shipmentId();

        // 건너뛰기 불가, 배송완료 전 구매확정 불가
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> seller(shipment, ShipmentStatus.SHIPPING));
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> seller(shipment, ShipmentStatus.DELIVERED));
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.confirm(buyer, order.orderId(), shipment));

        assertThat(seller(shipment, ShipmentStatus.PREPARING).status()).isEqualTo(ShipmentStatus.PREPARING);
        // 배송준비부터는 취소 불가, 같은 상태로 다시 바꾸기 불가
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.cancel(buyer, order.orderId(), shipment));
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> seller(shipment, ShipmentStatus.PREPARING));

        seller(shipment, ShipmentStatus.SHIPPING);
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> seller(shipment, ShipmentStatus.PREPARING)); // 되돌리기 불가
        seller(shipment, ShipmentStatus.DELIVERED);

        OrderResponse confirmed = orderService.confirm(buyer, order.orderId(), shipment);
        assertThat(confirmed.shipments().get(0).status()).isEqualTo(ShipmentStatus.CONFIRMED);
        assertThat(confirmed.shipments().get(0).confirmedAt()).isEqualTo(OrderCreateDbTest.ORDERED_AT);

        // 구매확정 뒤에는 아무것도 바꿀 수 없다
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.confirm(buyer, order.orderId(), shipment));
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> orderService.cancel(buyer, order.orderId(), shipment));
        assertError(ErrorCode.INVALID_ORDER_STATUS, () -> seller(shipment, ShipmentStatus.DELIVERED));
        assertThat(stockOf(teeBlack)).isEqualTo(99); // 재고는 주문 때 한 번 줄어든 그대로
        assertIntegrity();
    }

    @Test
    void 판매자가_둘인_주문에서_한쪽만_취소하면_그쪽_재고만_돌아오고_총액은_그대로다() {
        OrderResponse order = order(buyer, teeBlack, 2, capBlack, 3);
        long shipmentA = shipmentOf(order, teeBlack);
        long shipmentB = shipmentOf(order, capBlack);

        OrderResponse after = orderService.cancel(buyer, order.orderId(), shipmentA);

        assertThat(after.totalPrice()).isEqualTo(10_000 * 2 + 19_000 * 3); // 취소돼도 주문 당시 금액 그대로
        assertThat(after.shipments()).extracting(ShipmentResponse::shipmentId, ShipmentResponse::status)
                .containsExactly(org.assertj.core.api.Assertions.tuple(shipmentA, ShipmentStatus.CANCELED),
                        org.assertj.core.api.Assertions.tuple(shipmentB, ShipmentStatus.ORDERED));
        assertThat(after.shipments().get(0).canceledAt()).isNotNull();
        assertThat(stockOf(teeBlack)).isEqualTo(100);
        assertThat(stockOf(capBlack)).isEqualTo(97);
        // 판매자 B 는 자기 묶음을 계속 진행할 수 있다
        assertThat(sellerShipmentService.changeStatus(sellerUserB, shipmentB, ShipmentStatus.PREPARING).status())
                .isEqualTo(ShipmentStatus.PREPARING);
        assertIntegrity();
    }

    // ===== 소유권 =====

    @Test
    void 남의_주문과_남의_배송_묶음은_보거나_바꿀_수_없다() {
        OrderResponse order = order(buyer, teeBlack, 1, capBlack, 1);
        long shipmentA = shipmentOf(order, teeBlack);
        OrderResponse otherOrder = order(otherBuyer, teeWhite, 1);

        // 다른 구매자
        assertError(ErrorCode.NOT_FOUND, () -> orderService.getMyOrder(otherBuyer, order.orderId()));
        assertError(ErrorCode.NOT_FOUND, () -> orderService.cancel(otherBuyer, order.orderId(), shipmentA));
        assertError(ErrorCode.NOT_FOUND, () -> orderService.confirm(otherBuyer, order.orderId(), shipmentA));
        // 내 주문 번호에 남의 배송 묶음 번호를 끼워 넣기
        assertError(ErrorCode.NOT_FOUND, () -> orderService.cancel(otherBuyer, otherOrder.orderId(), shipmentA));
        // 다른 판매자
        assertError(ErrorCode.NOT_FOUND,
                () -> sellerShipmentService.changeStatus(sellerUserB, shipmentA, ShipmentStatus.PREPARING));

        assertThat(statusOf(shipmentA)).isEqualTo("ORDERED");
        assertThat(orderService.myOrders(otherBuyer, 0, 20).content()).extracting(OrderResponse::orderId)
                .containsExactly(otherOrder.orderId());
        // 판매자 B 의 목록에는 자기 묶음만, 줄도 자기 상품만
        PageResponse<SellerShipmentResponse> sellerB = sellerShipmentService.myShipments(sellerUserB, null, 0, 20);
        assertThat(sellerB.content()).hasSize(1);
        assertThat(sellerB.content().get(0).items()).extracting("optionId").containsExactly(capBlack);
        assertThat(sellerB.content().get(0).receiverName()).isEqualTo(OrderCreateDbTest.ADDRESS.receiverName());
    }

    // ===== 목록: 순서·필터·쿼리 수 =====

    @Test
    void 내_주문_목록은_최신순이고_주문이_몇_건이든_쿼리는_5번_이하다() {
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            created.add(order(buyer, teeBlack, 1, capBlack, 1).orderId()); // 주문마다 배송 묶음 2개
        }
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        PageResponse<OrderResponse> page = orderService.myOrders(buyer, 0, 4);

        long queries = stats.getPrepareStatementCount();
        stats.setStatisticsEnabled(false);
        assertThat(queries).isLessThanOrEqualTo(5); // 주문 페이지 + 전체 건수 + 주문 줄 + 결제 + 환불 (V7)
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.content()).extracting(OrderResponse::orderId)
                .containsExactly(created.get(5), created.get(4), created.get(3), created.get(2)); // 나중 주문이 먼저
        assertThat(page.content()).allSatisfy(o -> {
            assertThat(o.shipments()).hasSize(2);
            assertThat(o.shipments()).allSatisfy(s -> assertThat(s.items()).hasSize(1));
        });
        assertThat(orderService.myOrders(buyer, 1, 4).content()).extracting(OrderResponse::orderId)
                .containsExactly(created.get(1), created.get(0));
    }

    @Test
    void 판매자_배송_목록은_상태로_거를_수_있고_건수와_상관없이_쿼리_수가_같다() {
        List<Long> shipments = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            shipments.add(order(buyer, teeBlack, 1, teeWhite, 1).shipments().get(0).shipmentId());
        }
        seller(shipments.get(0), ShipmentStatus.PREPARING);
        seller(shipments.get(1), ShipmentStatus.PREPARING);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        PageResponse<SellerShipmentResponse> all = sellerShipmentService.myShipments(sellerUserA, null, 0, 3);

        long queries = stats.getPrepareStatementCount();
        stats.setStatisticsEnabled(false);
        assertThat(queries).isLessThanOrEqualTo(4); // 판매자 확인 + 배송 묶음 페이지 + 전체 건수 + 주문 줄
        assertThat(all.totalElements()).isEqualTo(5);
        assertThat(all.content()).extracting(SellerShipmentResponse::shipmentId)
                .containsExactly(shipments.get(4), shipments.get(3), shipments.get(2)); // 최신순
        assertThat(all.content()).allSatisfy(s -> assertThat(s.items()).hasSize(2));

        assertThat(sellerShipmentService.myShipments(sellerUserA, ShipmentStatus.PREPARING, 0, 20).content())
                .extracting(SellerShipmentResponse::shipmentId)
                .containsExactly(shipments.get(1), shipments.get(0));
        assertThat(sellerShipmentService.myShipments(sellerUserA, ShipmentStatus.CONFIRMED, 0, 20).content())
                .isEmpty();
    }

    // ===== helpers =====

    /** 장바구니에 담고 주문한다: (옵션, 수량) 을 번갈아 넘긴다 */
    private OrderResponse order(long buyerId, long... optionAndQuantity) {
        for (int i = 0; i < optionAndQuantity.length; i += 2) {
            cartService.add(buyerId, new CartItemAddRequest(optionAndQuantity[i], (int) optionAndQuantity[i + 1]));
        }
        return orderService.create(buyerId, OrderCreateDbTest.ADDRESS);
    }

    private long shipmentOf(OrderResponse order, long optionId) {
        return order.shipments().stream()
                .filter(s -> s.items().stream().anyMatch(i -> i.optionId() == optionId))
                .findFirst().orElseThrow().shipmentId();
    }

    private SellerShipmentResponse seller(long shipmentId, ShipmentStatus target) {
        return sellerShipmentService.changeStatus(sellerUserA, shipmentId, target);
    }

    private String outcome(Callable<?> call) {
        try {
            call.call();
            return "OK";
        } catch (BusinessException e) {
            return e.getErrorCode().name();
        } catch (Exception e) {
            return e.getClass().getSimpleName(); // 데드락 등 예상하지 못한 실패
        }
    }

    /** 모든 작업을 같은 순간에 출발시키고 결과를 넣은 순서대로 돌려준다 */
    private List<String> runTogether(List<Callable<String>> tasks) throws Exception {
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

    /** 이 테스트가 만든 데이터에 대해 정합성 검사와 같은 규칙을 확인한다 (어긋난 건수가 0 이어야 한다) */
    private void assertIntegrity() {
        String myLines = """
                 FROM order_items oi
                 JOIN shipments sh ON sh.id = oi.shipment_id
                 JOIN orders o ON o.id = sh.order_id
                 JOIN users u ON u.id = o.user_id
                WHERE u.email LIKE 'manage-test-%'""";
        assertThat(count("SELECT COUNT(*)" + myLines
                + " AND (SELECT COUNT(*) FROM stock_history h WHERE h.order_item_id = oi.id AND h.type = 'ORDER') <> 1"))
                .as("13 주문 줄마다 ORDER 이력 1건").isZero();
        assertThat(count("SELECT COUNT(*)" + myLines
                + " AND (SELECT COUNT(*) FROM stock_history h WHERE h.order_item_id = oi.id AND h.type = 'CANCEL')"
                + " <> IF(sh.status = 'CANCELED', 1, 0)"))
                .as("14 취소된 묶음의 줄만 CANCEL 이력 1건").isZero();
        assertThat(count("SELECT COUNT(*)" + myLines
                + " AND COALESCE(sh.confirmed_at, sh.canceled_at, o.created_at) < o.created_at"))
                .as("15 취소·확정 시각은 주문 이후").isZero();
        for (long option : List.of(teeBlack, teeWhite, capBlack)) {
            List<Integer> afters = jdbc.queryForList(
                    "SELECT quantity_after FROM stock_history WHERE option_id = ? ORDER BY id", Integer.class, option);
            List<Integer> changes = jdbc.queryForList(
                    "SELECT change_qty FROM stock_history WHERE option_id = ? ORDER BY id", Integer.class, option);
            for (int i = 1; i < afters.size(); i++) {
                assertThat(afters.get(i)).as("03 이력이 끊기지 않음").isEqualTo(afters.get(i - 1) + changes.get(i));
            }
            if (!afters.isEmpty()) {
                assertThat(stockOf(option)).as("01 재고 = 마지막 이력").isEqualTo(afters.get(afters.size() - 1));
            }
        }
    }

    private void assertError(ErrorCode expected, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private int stockOf(long optionId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM product_options WHERE id = ?", Integer.class, optionId);
    }

    private String statusOf(long shipmentId) {
        return jdbc.queryForObject("SELECT status FROM shipments WHERE id = ?", String.class, shipmentId);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
