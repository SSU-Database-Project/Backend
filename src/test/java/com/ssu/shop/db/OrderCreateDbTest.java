package com.ssu.shop.db;

import static com.ssu.shop.support.DbFixtures.BLACK;
import static com.ssu.shop.support.DbFixtures.WHITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.ssu.shop.domain.cart.dto.CartDtos.CartItemAddRequest;
import com.ssu.shop.domain.cart.dto.CartDtos.CartItemUpdateRequest;
import com.ssu.shop.domain.cart.dto.CartDtos.CartResponse;
import com.ssu.shop.domain.cart.repository.CartItemRepository;
import com.ssu.shop.domain.cart.service.CartService;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderCreateRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.PaymentRequest;
import com.ssu.shop.domain.payment.entity.PaymentMethod;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderResponse;
import com.ssu.shop.domain.order.entity.ShipmentStatus;
import com.ssu.shop.domain.order.service.OrderService;
import com.ssu.shop.domain.product.dto.ProductDtos.AdminProductStatusRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.OptionRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductUpdateRequest;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.service.ProductService;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.DbFixtures;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 생성을 실제 MySQL 에서 검증한다: 동시 주문, 실패 시 전체 롤백, 판매자별 배송 묶음과 시각, 정합성.
 * 정합성 기준은 팀의 정합성 검사(scripts/check-integrity.sql)와 같다 —
 *  (01·03) 재고 = 마지막 이력, 이력이 끊기지 않음   (09) 주문 총액 = 주문 줄 합계
 *  (10) 주문마다 배송 묶음 1개 이상, 배송 묶음마다 줄 1개 이상   (11) 배송 묶음의 판매자 = 옵션의 판매자
 *  (13) 주문 줄마다 ORDER 이력 1건
 *
 * 실행: DB_TEST_URL 이 있을 때만. 실제 커밋이 필요해 @Transactional 을 쓰지 않고 전용 이름(order-test-*)으로 만든
 * 데이터를 앞뒤로 지운다. 시계를 2000-01-01 로 고정해 주문 시각이 통계 테스트의 기간과 겹치지 않게 한다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class OrderCreateDbTest {

    static final LocalDateTime ORDERED_AT = LocalDateTime.of(2000, 1, 1, 10, 0);
    static final OrderCreateRequest ADDRESS =
            new OrderCreateRequest("테스트", "010-1234-5678", "06978", "서울특별시 동작구 상도로 369", "주문 테스트",
                    new PaymentRequest(PaymentMethod.CARD, "1234-5678-9012-3456"));

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId seoul = ZoneId.of("Asia/Seoul");
            return Clock.fixed(ORDERED_AT.atZone(seoul).toInstant(), seoul);
        }
    }

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
    CartService cartService;
    @Autowired
    ProductService productService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    PlatformTransactionManager txManager;

    DbFixtures db;

    long sellerUserA;
    long sellerA;
    long sellerB;
    long tee;       // 판매자 A, 10,000원
    long teeBlack;  // 재고 10
    long teeWhite;  // 재고 1
    long cap;       // 판매자 B, 19,000원
    long capBlack;  // 재고 5

    @BeforeEach
    void setUp() {
        db = new DbFixtures(jdbc, "order-test");
        db.cleanUp();
        sellerUserA = db.user("seller-a", "SELLER");
        sellerA = db.seller(sellerUserA, "7770000001");
        sellerB = db.seller(db.user("seller-b", "SELLER"), "7770000002");
        tee = db.product(db.brand(sellerA, "A"), "TEE", 10_000, "ON_SALE");
        teeBlack = db.option(tee, BLACK, 10);
        teeWhite = db.option(tee, WHITE, 1);
        cap = db.product(db.brand(sellerB, "B"), "CAP", 19_000, "ON_SALE");
        capBlack = db.option(cap, BLACK, 5);
    }

    @AfterEach
    void cleanUp() {
        db.cleanUp();
    }

    // ===== 동시성 =====

    @Test
    void 재고_10개에_20명이_동시에_주문하면_10건만_성공하고_재고는_0이_된다() throws Exception {
        int buyers = 20;
        List<Long> buyerIds = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            long buyer = db.user("buyer" + i, "USER");
            cartService.add(buyer, new CartItemAddRequest(teeBlack, 1));
            buyerIds.add(buyer);
        }

        List<String> outcomes = runTogether(buyerIds.stream()
                .<Callable<String>>map(buyer -> () -> orderOutcome(buyer)).toList());

        // 데드락이나 락 대기 초과가 났다면 OK / OUT_OF_STOCK 이 아닌 값이 섞인다
        assertThat(outcomes).filteredOn("OK"::equals).hasSize(10);
        assertThat(outcomes).filteredOn(ErrorCode.OUT_OF_STOCK.name()::equals).hasSize(10);
        assertThat(stockOf(teeBlack)).isZero();
        assertThat(count("SELECT COUNT(*) FROM orders o JOIN users u ON u.id = o.user_id WHERE u.email LIKE 'order-test-%'"))
                .isEqualTo(10);
        for (int i = 0; i < buyers; i++) {
            boolean ordered = "OK".equals(outcomes.get(i));
            // 성공한 회원의 장바구니는 비고, 실패한 회원의 장바구니는 그대로
            assertThat(cartService.getCart(buyerIds.get(i)).items()).hasSize(ordered ? 0 : 1);
        }
        assertIntegrity();
    }

    @Test
    void 같은_회원이_주문을_동시에_두_번_보내도_주문은_한_건만_생긴다() throws Exception {
        long buyer = db.user("buyer", "USER");
        cartService.add(buyer, new CartItemAddRequest(teeBlack, 2));

        List<String> outcomes = runTogether(List.of(() -> orderOutcome(buyer), () -> orderOutcome(buyer)));

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM orders o JOIN users u ON u.id = o.user_id WHERE u.email LIKE 'order-test-%'"))
                .isEqualTo(1);
        assertThat(stockOf(teeBlack)).isEqualTo(8); // 한 번만 차감
        assertIntegrity();
    }

    // ===== 롤백 =====

    @Test
    void 하나라도_재고가_모자라면_주문도_재고도_장바구니도_그대로다() {
        long buyer = db.user("buyer", "USER");
        cartService.add(buyer, new CartItemAddRequest(teeBlack, 2));
        cartService.add(buyer, new CartItemAddRequest(capBlack, 1));
        cartService.add(buyer, new CartItemAddRequest(teeWhite, 2)); // 재고 1

        assertError(ErrorCode.OUT_OF_STOCK, () -> orderService.create(buyer, ADDRESS));

        assertNothingOrdered(buyer, 3);
    }

    @Test
    void 판매중이_아닌_상품이_체크돼_있으면_전체를_거부하고_체크를_풀면_주문된다() {
        long buyer = db.user("buyer", "USER");
        cartService.add(buyer, new CartItemAddRequest(teeBlack, 1));
        CartResponse cart = cartService.add(buyer, new CartItemAddRequest(capBlack, 1));
        jdbc.update("UPDATE products SET status = 'SOLD_OUT' WHERE id = ?", cap); // 담은 뒤 판매자가 품절 처리

        assertError(ErrorCode.PRODUCT_NOT_PURCHASABLE, () -> orderService.create(buyer, ADDRESS));
        assertNothingOrdered(buyer, 2);

        long capItem = cart.items().stream().filter(i -> i.optionId() == capBlack).findFirst().orElseThrow()
                .cartItemId();
        cartService.update(buyer, capItem, new CartItemUpdateRequest(null, false));
        OrderResponse order = orderService.create(buyer, ADDRESS);

        assertThat(order.totalPrice()).isEqualTo(10_000);
        // 체크하지 않은 항목은 장바구니에 남는다
        assertThat(cartService.getCart(buyer).items()).extracting("optionId").containsExactly(capBlack);
    }

    @Test
    void 체크한_항목이_없으면_NO_ORDER_ITEMS() {
        long buyer = db.user("buyer", "USER");
        assertError(ErrorCode.NO_ORDER_ITEMS, () -> orderService.create(buyer, ADDRESS)); // 장바구니가 아예 없음

        long item = cartService.add(buyer, new CartItemAddRequest(teeBlack, 1)).items().get(0).cartItemId();
        cartService.update(buyer, item, new CartItemUpdateRequest(null, false));
        assertError(ErrorCode.NO_ORDER_ITEMS, () -> orderService.create(buyer, ADDRESS)); // 담았지만 체크 안 함
    }

    /**
     * 주문이 장바구니를 읽은 뒤 끝나기 전에, 같은 회원의 다른 요청이 그 항목의 수량을 바꾸거나 체크를 푼 경우.
     * 예전 값으로 주문하고 바뀐 항목까지 지우면 안 된다 → 주문 전체를 DATA_CONFLICT 로 거부하고 바뀐 장바구니는 남긴다.
     * 순서를 고정하려고, 바깥 트랜잭션에서 장바구니를 먼저 읽고 → 다른 스레드가 변경을 커밋한 뒤 → 같은 트랜잭션에서 주문한다.
     */
    @Test
    void 주문하는_사이_수량이나_체크가_바뀌면_DATA_CONFLICT_이고_바뀐_장바구니는_그대로_남는다() {
        long buyer = db.user("buyer", "USER");
        long itemId = cartService.add(buyer, new CartItemAddRequest(teeBlack, 1)).items().get(0).cartItemId();

        // 1) 수량 변경: 주문은 수량 1 로 읽었는데 그 사이 3 이 됨
        assertError(ErrorCode.DATA_CONFLICT, () -> orderAfterConcurrentChange(buyer,
                () -> cartService.update(buyer, itemId, new CartItemUpdateRequest(3, null))));
        assertNothingOrdered(buyer, 1);
        assertThat(cartService.getCart(buyer).items().get(0).quantity()).isEqualTo(3);

        // 2) 체크 해제: 주문은 체크된 항목으로 읽었는데 그 사이 체크를 풂
        assertError(ErrorCode.DATA_CONFLICT, () -> orderAfterConcurrentChange(buyer,
                () -> cartService.update(buyer, itemId, new CartItemUpdateRequest(null, false))));
        assertNothingOrdered(buyer, 1);
        assertThat(cartService.getCart(buyer).items().get(0).selected()).isFalse();

        // 바뀐 상태로 다시 주문하면 정상 (체크를 다시 하고 수량 3 으로)
        cartService.update(buyer, itemId, new CartItemUpdateRequest(null, true));
        assertThat(orderService.create(buyer, ADDRESS).shipments().get(0).items().get(0).quantity()).isEqualTo(3);
        assertThat(cartService.getCart(buyer).items()).isEmpty();
        assertThat(stockOf(teeBlack)).isEqualTo(7);
    }

    // ===== 주문과 판매자의 상품 변경이 겹칠 때 =====

    /**
     * 주문이 장바구니를 읽은 뒤 잠금을 잡기 전에(= 잠금을 기다리는 사이에) 판매자의 변경이 커밋된 경우.
     * MySQL 은 일반 SELECT 에 트랜잭션이 처음 읽은 시점의 값을 계속 보여 주므로, 처음 읽어 둔 상품 상태로 검사하면
     * 이미 품절·삭제·수정된 상품이 바뀌기 전 상태와 가격으로 주문된다 → 상품을 잠근 뒤 다시 읽은 값으로 검사해야 한다.
     * 순서 고정은 위 테스트와 같다: 바깥 트랜잭션에서 장바구니를 먼저 읽고 → 다른 스레드가 판매자의 변경을 커밋 → 같은 트랜잭션에서 주문.
     */
    @Test
    void 주문이_잠금을_기다리는_사이_판매자가_품절_처리하면_주문을_거부하고_아무것도_바뀌지_않는다() {
        long buyer = buyerWithTeeAndCap();

        assertError(ErrorCode.PRODUCT_NOT_PURCHASABLE, () -> orderAfterConcurrentChange(buyer,
                () -> productService.changeSaleStatus(sellerUserA, tee, ProductStatus.SOLD_OUT)));

        assertThat(statusOf(tee)).isEqualTo("SOLD_OUT");
        assertNothingOrdered(buyer, 2);
    }

    @Test
    void 주문이_잠금을_기다리는_사이_판매자가_상품을_삭제하면_주문을_거부하고_아무것도_바뀌지_않는다() {
        long buyer = buyerWithTeeAndCap();

        assertError(ErrorCode.PRODUCT_NOT_PURCHASABLE, () -> orderAfterConcurrentChange(buyer,
                () -> productService.delete(sellerUserA, tee)));

        assertThat(statusOf(tee)).isEqualTo("DELETED");
        assertNothingOrdered(buyer, 2);
    }

    @Test
    void 주문이_잠금을_기다리는_사이_판매자가_가격을_수정하면_재승인_대기라_주문을_거부하고_아무것도_바뀌지_않는다() {
        long buyer = buyerWithTeeAndCap();

        assertError(ErrorCode.PRODUCT_NOT_PURCHASABLE, () -> orderAfterConcurrentChange(buyer,
                () -> productService.update(sellerUserA, tee, priceAndName(12_000, null))));

        assertThat(statusOf(tee)).isEqualTo("PENDING");
        assertNothingOrdered(buyer, 2);
    }

    /** 상태뿐 아니라 주문 줄에 복사하는 상품명·단가와 주문 총액도 잠근 뒤 읽은 값이어야 한다 */
    @Test
    void 주문이_잠금을_기다리는_사이_가격_수정과_재승인이_끝났으면_바뀐_이름과_가격으로_주문된다() {
        long buyer = buyerWithTeeAndCap(); // tee 2개 + cap 1개

        OrderResponse order = orderAfterConcurrentChange(buyer, () -> {
            productService.update(sellerUserA, tee, priceAndName(12_000, "새 이름 티셔츠"));
            productService.adminChangeStatus(tee, new AdminProductStatusRequest(ProductStatus.ON_SALE, null));
        });

        assertThat(order.totalPrice()).isEqualTo(12_000 * 2 + 19_000);
        assertThat(order.shipments().get(0).items()).extracting("productName", "unitPrice", "lineTotal")
                .containsExactly(tuple("새 이름 티셔츠", 12_000, 24_000L));
        assertThat(count("SELECT total_price FROM orders WHERE id = " + order.orderId())).isEqualTo(43_000);
        assertThat(count("SELECT COUNT(*) FROM order_items WHERE option_id = %d AND unit_price = 12000"
                .formatted(teeBlack) + " AND product_name = '새 이름 티셔츠'")).isEqualTo(1);
        assertIntegrity();
    }

    /**
     * 반대 순서: 주문이 먼저 상품을 잡았으면, 판매자의 변경은 주문이 끝날 때까지 기다렸다가 그 뒤에 처리된다.
     * (주문이 검사한 상태가 주문이 커밋될 때까지 바뀌지 않는다.)
     */
    @Test
    void 주문이_끝나기_전에는_판매자의_상품_변경이_기다렸다가_주문_뒤에_처리된다() throws Exception {
        long buyer = db.user("buyer", "USER");
        cartService.add(buyer, new CartItemAddRequest(teeBlack, 1));
        ExecutorService seller = Executors.newSingleThreadExecutor();
        try {
            Future<?> change = new TransactionTemplate(txManager).execute(status -> {
                orderService.create(buyer, ADDRESS); // 아직 커밋 전 — 이 주문이 상품을 잡고 있다
                Future<?> waiting = seller.submit(
                        () -> productService.changeSaleStatus(sellerUserA, tee, ProductStatus.SOLD_OUT));
                assertThatThrownBy(() -> waiting.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                return waiting;
            });
            change.get(30, TimeUnit.SECONDS); // 주문이 커밋되면 이어서 처리된다
        } finally {
            seller.shutdownNow();
        }

        assertThat(count("SELECT COUNT(*) FROM orders WHERE user_id = " + buyer)).isEqualTo(1);
        assertThat(stockOf(teeBlack)).isEqualTo(9);
        assertThat(statusOf(tee)).isEqualTo("SOLD_OUT");
        assertIntegrity();
    }

    /**
     * 주문(상품 여러 개를 잠근 뒤 옵션을 잠금)과 판매자·관리자의 상품 변경(상품 하나만 잠금)이 한꺼번에 몰려도
     * 서로를 기다리다 멈추지 않고 하나씩 처리되는지 확인한다. 타이밍 문제라 회원과 가격을 바꿔 가며 반복한다.
     * 데드락이나 잠금 대기 초과가 나면 결과에 OK / PRODUCT_NOT_PURCHASABLE 이 아닌 값(예외 이름)이 섞인다.
     * 장바구니에는 상품 id 순서와 다르게 담는다 — 주문이 담은 순서가 아니라 상품 id 순서로 잠그는지도 함께 걸린다.
     */
    @Test
    void 주문과_판매자의_상품_변경이_동시에_와도_데드락_없이_하나씩_처리된다() throws Exception {
        int rounds = 10;
        int buyersPerRound = 3;
        long shirt = db.product(brandOf(tee), "RACE-SHIRT", 10_000, "ON_SALE");  // 판매자 A
        long shirtBlack = db.option(shirt, BLACK, 1_000);
        long coat = db.product(brandOf(tee), "RACE-COAT", 20_000, "ON_SALE");    // 판매자 A
        long coatBlack = db.option(coat, BLACK, 1_000);
        long bag = db.product(brandOf(cap), "RACE-BAG", 30_000, "ON_SALE");      // 판매자 B
        long bagBlack = db.option(bag, BLACK, 1_000);
        int ordered = 0;

        for (int round = 0; round < rounds; round++) {
            jdbc.update("UPDATE products SET status = 'ON_SALE' WHERE id IN (?, ?, ?)", shirt, coat, bag);
            int oldPrice = 10_000 + round;
            int newPrice = oldPrice + 1;
            long newColor = 3 + round; // 블랙(1)·화이트(2) 다음 색상부터 하나씩 옵션 추가
            List<Long> buyers = new ArrayList<>();
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < buyersPerRound; i++) {
                long buyer = db.user("race" + round + "-" + i, "USER");
                cartService.add(buyer, new CartItemAddRequest(coatBlack, 1));
                cartService.add(buyer, new CartItemAddRequest(bagBlack, 1));
                cartService.add(buyer, new CartItemAddRequest(shirtBlack, 1));
                buyers.add(buyer);
                tasks.add(() -> orderOutcome(buyer));
            }
            // 판매자: 가격 수정 + 옵션 추가(→ 재승인 대기), 품절 처리 / 관리자: 차단
            tasks.add(() -> outcomeOf(() -> productService.update(sellerUserA, shirt, new ProductUpdateRequest(
                    null, null, null, null, newPrice, null, null, null, null,
                    List.of(new OptionRequest(newColor, DbFixtures.SIZE_M, 5))))));
            tasks.add(() -> outcomeOf(
                    () -> productService.changeSaleStatus(sellerUserA, coat, ProductStatus.SOLD_OUT)));
            tasks.add(() -> outcomeOf(() -> productService.adminChangeStatus(bag,
                    new AdminProductStatusRequest(ProductStatus.BLOCKED, null))));

            List<String> outcomes = runTogether(tasks);

            assertThat(outcomes.subList(buyersPerRound, outcomes.size())).as("상품 변경 (round %d)", round)
                    .containsOnly("OK");
            assertThat(outcomes.subList(0, buyersPerRound)).as("주문 (round %d)", round)
                    .isSubsetOf("OK", ErrorCode.PRODUCT_NOT_PURCHASABLE.name());
            for (int i = 0; i < buyersPerRound; i++) {
                long buyer = buyers.get(i);
                if ("OK".equals(outcomes.get(i))) {
                    ordered++;
                    // 판매중으로 보고 주문했다면 가격도 바뀌기 전 값이어야 한다 (상태와 가격은 한 번에 바뀐다)
                    assertThat(count("SELECT total_price FROM orders WHERE user_id = " + buyer))
                            .isEqualTo(oldPrice + 20_000 + 30_000);
                    assertThat(cartService.getCart(buyer).items()).isEmpty();
                } else {
                    assertThat(count("SELECT COUNT(*) FROM orders WHERE user_id = " + buyer)).isZero();
                    assertThat(cartService.getCart(buyer).items()).hasSize(3);
                }
            }
            assertThat(statusOf(shirt)).isEqualTo("PENDING");
            assertThat(count("SELECT price FROM products WHERE id = " + shirt)).isEqualTo(newPrice);
            assertThat(statusOf(coat)).isEqualTo("SOLD_OUT");
            assertThat(statusOf(bag)).isEqualTo("BLOCKED");
        }

        // 성공한 주문만큼만 재고가 줄었다
        assertThat(stockOf(shirtBlack)).isEqualTo(1_000 - ordered);
        assertThat(stockOf(coatBlack)).isEqualTo(1_000 - ordered);
        assertThat(stockOf(bagBlack)).isEqualTo(1_000 - ordered);
        assertIntegrity(List.of(shirtBlack, coatBlack, bagBlack));
    }

    // ===== 주문 내용 =====

    @Test
    void 판매자가_둘이면_배송_묶음도_둘이고_시각은_주문과_같고_주문_당시_값이_복사된다() {
        long buyer = db.user("buyer", "USER");
        cartService.add(buyer, new CartItemAddRequest(teeBlack, 2));
        cartService.add(buyer, new CartItemAddRequest(capBlack, 3));
        cartService.add(buyer, new CartItemAddRequest(teeWhite, 1));

        OrderResponse order = orderService.create(buyer, ADDRESS);

        // 응답
        assertThat(order.createdAt()).isEqualTo(ORDERED_AT);
        assertThat(order.totalPrice()).isEqualTo(10_000 * 2 + 19_000 * 3 + 10_000);
        assertThat(order.receiverPhone()).isEqualTo("01012345678");
        assertThat(order.shipments()).extracting("sellerId", "status", "subtotal").containsExactly(
                tuple(sellerA, ShipmentStatus.ORDERED, 30_000L),
                tuple(sellerB, ShipmentStatus.ORDERED, 57_000L));
        assertThat(order.shipments().get(0).items())
                .extracting("optionId", "productName", "colorName", "sizeLabel", "unitPrice", "quantity", "lineTotal")
                .containsExactly(
                        tuple(teeBlack, "테스트 상품 TEE", "블랙", "M", 10_000, 2, 20_000L),
                        tuple(teeWhite, "테스트 상품 TEE", "화이트", "M", 10_000, 1, 10_000L));

        // DB: 주문과 배송 묶음의 생성 시각이 같은 값
        assertThat(count("""
                SELECT COUNT(*) FROM shipments s JOIN orders o ON o.id = s.order_id
                 WHERE o.id = %d AND s.created_at = o.created_at AND o.created_at = '2000-01-01 10:00:00'"""
                .formatted(order.orderId()))).isEqualTo(2);
        // 가격이 나중에 바뀌어도 주문 줄의 단가와 주문 총액은 그대로
        jdbc.update("UPDATE products SET price = 99999, name = '이름 바뀜' WHERE id = ?", tee);
        assertThat(count("SELECT COUNT(*) FROM order_items WHERE option_id = %d AND unit_price = 10000".formatted(teeBlack)))
                .isEqualTo(1);
        // 재고 차감과 장바구니 비우기
        assertThat(stockOf(teeBlack)).isEqualTo(8);
        assertThat(stockOf(teeWhite)).isZero();
        assertThat(stockOf(capBlack)).isEqualTo(2);
        assertThat(cartService.getCart(buyer).items()).isEmpty();
        assertIntegrity();
    }

    // ===== helpers =====

    /** 한 트랜잭션 안에서: 체크한 장바구니를 먼저 읽고 → 다른 스레드가 change 를 커밋하게 한 뒤 → 주문한다 */
    private OrderResponse orderAfterConcurrentChange(long buyer, Runnable change) {
        return new TransactionTemplate(txManager).execute(status -> {
            assertThat(cartItemRepository.findSelectedForOrder(buyer)).isNotEmpty(); // 주문이 장바구니를 읽은 시점
            ExecutorService other = Executors.newSingleThreadExecutor();
            try {
                other.submit(change).get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                other.shutdown();
            }
            // 같은 트랜잭션에 합류 → 일반 SELECT 는 위에서 읽은 시점의 값을 계속 본다 (잠금 조회만 최신 값을 본다)
            return orderService.create(buyer, ADDRESS);
        });
    }

    /** 판매자 A 의 tee 2개와 판매자 B 의 cap 1개를 담은 새 구매자 */
    private long buyerWithTeeAndCap() {
        long buyer = db.user("buyer", "USER");
        cartService.add(buyer, new CartItemAddRequest(teeBlack, 2));
        cartService.add(buyer, new CartItemAddRequest(capBlack, 1));
        return buyer;
    }

    /** 가격(과 이름)만 바꾸는 상품 수정 요청. null 인 필드는 그대로 */
    private static ProductUpdateRequest priceAndName(int price, String name) {
        return new ProductUpdateRequest(null, null, name, null, price, null, null, null, null, null);
    }

    private String orderOutcome(long buyer) {
        return outcomeOf(() -> orderService.create(buyer, ADDRESS));
    }

    private String outcomeOf(Runnable work) {
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

    private void assertNothingOrdered(long buyer, int cartSize) {
        assertThat(count("SELECT COUNT(*) FROM orders WHERE user_id = " + buyer)).isZero();
        assertThat(stockOf(teeBlack)).isEqualTo(10);
        assertThat(stockOf(teeWhite)).isEqualTo(1);
        assertThat(stockOf(capBlack)).isEqualTo(5);
        assertThat(count("""
                SELECT COUNT(*) FROM stock_history h
                  JOIN product_options o ON o.id = h.option_id
                  JOIN products p ON p.id = o.product_id
                  JOIN brands b ON b.id = p.brand_id
                 WHERE b.name LIKE 'ORDER-TEST-%'""")).isZero();
        assertThat(cartService.getCart(buyer).items()).hasSize(cartSize);
    }

    /** 이 테스트가 만든 데이터에 대해 정합성 검사와 같은 규칙을 확인한다 (어긋난 건수가 0 이어야 한다) */
    private void assertIntegrity() {
        assertIntegrity(List.of(teeBlack, teeWhite, capBlack));
    }

    private void assertIntegrity(List<Long> options) {
        String mine = " JOIN users u ON u.id = o.user_id WHERE u.email LIKE 'order-test-%'";
        assertThat(count("""
                SELECT COUNT(*) FROM (
                  SELECT o.id FROM orders o
                    JOIN shipments sh ON sh.order_id = o.id
                    JOIN order_items oi ON oi.shipment_id = sh.id
                    JOIN users u ON u.id = o.user_id
                   WHERE u.email LIKE 'order-test-%'
                   GROUP BY o.id, o.total_price
                  HAVING o.total_price <> SUM(oi.unit_price * oi.quantity)) t""")).as("09 주문 총액 = 줄 합계").isZero();
        assertThat(count("SELECT COUNT(*) FROM orders o" + mine
                + " AND NOT EXISTS (SELECT 1 FROM shipments sh WHERE sh.order_id = o.id)")).as("10 주문마다 배송 묶음").isZero();
        assertThat(count("""
                SELECT COUNT(*) FROM shipments sh JOIN orders o ON o.id = sh.order_id""" + mine
                + " AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.shipment_id = sh.id)")).as("10 배송 묶음마다 줄").isZero();
        assertThat(count("""
                SELECT COUNT(*) FROM order_items oi
                  JOIN shipments sh ON sh.id = oi.shipment_id
                  JOIN product_options po ON po.id = oi.option_id
                  JOIN products p ON p.id = po.product_id
                  JOIN brands b ON b.id = p.brand_id
                 WHERE b.name LIKE 'ORDER-TEST-%' AND b.seller_id <> sh.seller_id""")).as("11 배송 묶음의 판매자").isZero();
        assertThat(count("""
                SELECT COUNT(*) FROM order_items oi
                  JOIN shipments sh ON sh.id = oi.shipment_id
                  JOIN orders o ON o.id = sh.order_id""" + mine + """
                   AND (SELECT COUNT(*) FROM stock_history h WHERE h.order_item_id = oi.id AND h.type = 'ORDER') <> 1"""))
                .as("13 주문 줄마다 ORDER 이력 1건").isZero();
        for (long option : options) {
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

    private String statusOf(long productId) {
        return jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, productId);
    }

    private long brandOf(long productId) {
        return jdbc.queryForObject("SELECT brand_id FROM products WHERE id = ?", Long.class, productId);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
