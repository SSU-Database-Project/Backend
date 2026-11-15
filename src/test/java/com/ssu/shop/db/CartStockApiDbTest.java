package com.ssu.shop.db;

import static com.ssu.shop.support.DbFixtures.BLACK;
import static com.ssu.shop.support.DbFixtures.WHITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.ssu.shop.domain.cart.dto.CartDtos.CartItemAddRequest;
import com.ssu.shop.domain.cart.dto.CartDtos.CartItemUpdateRequest;
import com.ssu.shop.domain.cart.dto.CartDtos.CartResponse;
import com.ssu.shop.domain.cart.dto.CartItemState;
import com.ssu.shop.domain.cart.entity.CartItem;
import com.ssu.shop.domain.cart.repository.CartItemRepository;
import com.ssu.shop.domain.cart.service.CartService;
import com.ssu.shop.domain.inventory.dto.StockDtos.StockChangeRequest;
import com.ssu.shop.domain.inventory.dto.StockDtos.StockHistoryResponse;
import com.ssu.shop.domain.inventory.dto.StockDtos.StockResponse;
import com.ssu.shop.domain.inventory.entity.StockType;
import com.ssu.shop.domain.inventory.service.SellerStockService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 장바구니와 판매자 재고 API 의 서비스를 실제 MySQL 에서 검증한다.
 * (담기의 INSERT ... ON DUPLICATE KEY UPDATE 는 MySQL 문법이라 DB 없이는 확인할 수 없다.)
 *
 * 실행: DB_TEST_URL 이 있을 때만. 서비스가 직접 커밋하므로 @Transactional 을 쓰지 않고,
 * 전용 이름(cart-test-*, CART-TEST-*)으로 만든 데이터를 테스트 앞뒤로 지운다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class CartStockApiDbTest {

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
    CartService cartService;
    @Autowired
    SellerStockService sellerStockService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManagerFactory emf;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    PlatformTransactionManager txManager;

    DbFixtures db;

    long sellerUser;
    long buyer;
    long otherBuyer;
    long tee;          // 판매중 상품 (10,000원)
    long teeBlack;     // 재고 5
    long teeWhite;     // 재고 0
    long hood;         // 판매자가 품절 처리한 상품 (20,000원)
    long hoodBlack;    // 재고 3
    long otherOption;  // 다른 판매자의 옵션

    @BeforeEach
    void setUp() {
        db = new DbFixtures(jdbc, "cart-test");
        db.cleanUp(); // 이전 실행이 중간에 죽어 남긴 데이터가 있어도 다시 시작할 수 있게
        sellerUser = db.user("seller", "SELLER");
        long brand = db.brand(db.seller(sellerUser, "8880000001"), "A");
        buyer = db.user("buyer", "USER");
        otherBuyer = db.user("buyer2", "USER");

        tee = db.product(brand, "TEE", 10_000, "ON_SALE");
        teeBlack = db.option(tee, BLACK, 5);
        teeWhite = db.option(tee, WHITE, 0);
        hood = db.product(brand, "HOOD", 20_000, "SOLD_OUT");
        hoodBlack = db.option(hood, BLACK, 3);

        long otherBrand = db.brand(db.seller(db.user("seller2", "SELLER"), "8880000002"), "B");
        otherOption = db.option(db.product(otherBrand, "OTHER", 5_000, "ON_SALE"), BLACK, 9);
    }

    @AfterEach
    void cleanUp() {
        db.cleanUp();
    }

    // ===== 장바구니 =====

    @Test
    void 같은_옵션을_다시_담으면_줄이_늘지_않고_수량만_늘어난다() {
        cartService.add(buyer, new CartItemAddRequest(teeBlack, 2));
        CartResponse cart = cartService.add(buyer, new CartItemAddRequest(teeBlack, 3));

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(5);
    }

    @Test
    void 재고보다_많이_담을_수_있지만_합쳐서_99를_넘으면_거부하고_수량은_그대로다() {
        cartService.add(buyer, new CartItemAddRequest(teeBlack, 50)); // 재고는 5
        CartResponse cart = cartService.add(buyer, new CartItemAddRequest(teeBlack, 49));
        assertThat(cart.items().get(0).quantity()).isEqualTo(99);
        assertThat(cart.items().get(0).stock()).isEqualTo(5);

        assertError(ErrorCode.INVALID_INPUT, () -> cartService.add(buyer, new CartItemAddRequest(teeBlack, 1)));

        assertThat(cartService.getCart(buyer).items().get(0).quantity()).isEqualTo(99); // 더한 1 은 롤백됨
    }

    @Test
    void 조회는_현재_가격과_재고와_상태를_보여주고_합계는_체크된_구매가능_항목만_더한다() {
        long pendingSoon = db.option(db.product(brandOf(tee), "COAT", 30_000, "ON_SALE"), BLACK, 4);
        cartService.add(buyer, new CartItemAddRequest(teeBlack, 2));
        cartService.add(buyer, new CartItemAddRequest(teeWhite, 1));
        cartService.add(buyer, new CartItemAddRequest(hoodBlack, 1));
        cartService.add(buyer, new CartItemAddRequest(pendingSoon, 1));
        // 담은 뒤에 판매자가 상품을 수정해 재승인 대기가 된 상황
        jdbc.update("UPDATE products SET status = 'PENDING' WHERE product_code = 'COAT' AND brand_id = ?", brandOf(tee));

        CartResponse cart = cartService.getCart(buyer);

        assertThat(cart.items()).extracting("optionId", "price", "stock", "state", "lineTotal").containsExactly(
                tuple(teeBlack, 10_000, 5, CartItemState.AVAILABLE, 20_000L),
                tuple(teeWhite, 10_000, 0, CartItemState.SOLD_OUT, 10_000L),      // 옵션 재고 0
                tuple(hoodBlack, 20_000, 3, CartItemState.SOLD_OUT, 20_000L),     // 판매자 품절 처리
                tuple(pendingSoon, 30_000, 4, CartItemState.UNAVAILABLE, 30_000L));
        assertThat(cart.selectedCount()).isEqualTo(1);
        assertThat(cart.totalPrice()).isEqualTo(20_000L); // 구매 가능한 teeBlack 만

        // 가격은 저장하지 않으므로 상품 가격이 바뀌면 장바구니도 바뀐다
        jdbc.update("UPDATE products SET price = 12000 WHERE id = ?", tee);
        assertThat(cartService.getCart(buyer).totalPrice()).isEqualTo(24_000L);

        // 체크를 풀면 합계에서 빠진다
        long itemId = cart.items().get(0).cartItemId();
        CartResponse unchecked = cartService.update(buyer, itemId, new CartItemUpdateRequest(null, false));
        assertThat(unchecked.selectedCount()).isZero();
        assertThat(unchecked.totalPrice()).isZero();
    }

    @Test
    void 수량과_체크를_바꾸면_DB에_남고_삭제하면_사라진다() {
        long itemId = cartService.add(buyer, new CartItemAddRequest(teeBlack, 1)).items().get(0).cartItemId();

        cartService.update(buyer, itemId, new CartItemUpdateRequest(3, false));
        CartResponse cart = cartService.getCart(buyer); // 다시 들어와도 유지
        assertThat(cart.items().get(0).quantity()).isEqualTo(3);
        assertThat(cart.items().get(0).selected()).isFalse();

        cartService.update(buyer, itemId, new CartItemUpdateRequest(null, true)); // 수량은 그대로
        assertThat(cartService.getCart(buyer).items().get(0).quantity()).isEqualTo(3);

        assertThat(cartService.remove(buyer, itemId).items()).isEmpty();
    }

    @Test
    void 남의_장바구니_항목은_바꾸거나_지울_수_없다() {
        long itemId = cartService.add(buyer, new CartItemAddRequest(teeBlack, 1)).items().get(0).cartItemId();

        assertError(ErrorCode.NOT_FOUND,
                () -> cartService.update(otherBuyer, itemId, new CartItemUpdateRequest(9, null)));
        assertError(ErrorCode.NOT_FOUND, () -> cartService.remove(otherBuyer, itemId));

        assertThat(cartService.getCart(buyer).items().get(0).quantity()).isEqualTo(1);
        assertThat(cartService.getCart(otherBuyer).items()).isEmpty();
    }

    @Test
    void 구매자에게_보이지_않는_상품이나_없는_옵션은_담을_수_없다() {
        long hidden = db.option(db.product(brandOf(tee), "HIDDEN", 1_000, "PENDING"), BLACK, 5);

        assertError(ErrorCode.NOT_FOUND, () -> cartService.add(buyer, new CartItemAddRequest(hidden, 1)));
        assertError(ErrorCode.NOT_FOUND, () -> cartService.add(buyer, new CartItemAddRequest(999_999_999L, 1)));
        assertThat(cartService.getCart(buyer).items()).isEmpty();
    }

    @Test
    void 장바구니_조회는_항목_수와_상관없이_쿼리_1번이다() {
        cartService.add(buyer, new CartItemAddRequest(teeBlack, 1));
        cartService.add(buyer, new CartItemAddRequest(teeWhite, 1));
        cartService.add(buyer, new CartItemAddRequest(hoodBlack, 1));
        cartService.add(buyer, new CartItemAddRequest(otherOption, 1)); // 다른 브랜드
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        CartResponse cart = cartService.getCart(buyer);

        assertThat(cart.items()).hasSize(4);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        stats.setStatisticsEnabled(false);
    }

    // ===== 판매자 입고·조정·이력 =====

    @Test
    void 입고와_조정은_재고와_이력을_함께_바꾸고_이력은_최신순으로_나온다() {
        sellerStockService.change(sellerUser, teeBlack, new StockChangeRequest(StockType.IN, 20, "11월 입고"));
        StockResponse after = sellerStockService.change(sellerUser, teeBlack,
                new StockChangeRequest(StockType.ADJUST, -5, "파손"));

        assertThat(after.stockQuantity()).isEqualTo(20); // 5 + 20 - 5
        assertThat(stockOf(teeBlack)).isEqualTo(20);

        PageResponse<StockHistoryResponse> history = sellerStockService.history(sellerUser, teeBlack, 0, 20);
        assertThat(history.totalElements()).isEqualTo(2);
        assertThat(history.content()).extracting("type", "changeQty", "quantityAfter", "memo").containsExactly(
                tuple(StockType.ADJUST, -5, 20, "파손"),
                tuple(StockType.IN, 20, 25, "11월 입고"));
        assertThat(history.content().get(0).quantityAfter()).isEqualTo(stockOf(teeBlack)); // 재고 = 마지막 이력

        // 페이지 크기 1 → 가장 최근 1건만
        PageResponse<StockHistoryResponse> firstPage = sellerStockService.history(sellerUser, teeBlack, 0, 1);
        assertThat(firstPage.content()).extracting("type").containsExactly(StockType.ADJUST);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    @Test
    void 남의_옵션과_삭제된_상품과_음수가_되는_조정은_거부하고_재고는_그대로다() {
        assertError(ErrorCode.PRODUCT_NOT_OWNED, () -> sellerStockService.change(sellerUser, otherOption,
                new StockChangeRequest(StockType.IN, 10, null)));
        assertError(ErrorCode.PRODUCT_NOT_OWNED, () -> sellerStockService.history(sellerUser, otherOption, 0, 20));
        assertThat(stockOf(otherOption)).isEqualTo(9);

        assertError(ErrorCode.INVALID_INPUT, () -> sellerStockService.change(sellerUser, teeBlack,
                new StockChangeRequest(StockType.ADJUST, -6, null))); // 재고 5 → -1
        assertError(ErrorCode.INVALID_INPUT, () -> sellerStockService.change(sellerUser, teeBlack,
                new StockChangeRequest(StockType.IN, -1, null)));

        jdbc.update("UPDATE products SET status = 'DELETED' WHERE id = ?", tee);
        assertError(ErrorCode.PRODUCT_NOT_EDITABLE, () -> sellerStockService.change(sellerUser, teeBlack,
                new StockChangeRequest(StockType.IN, 10, null)));

        assertThat(stockOf(teeBlack)).isEqualTo(5);
        assertThat(sellerStockService.history(sellerUser, teeBlack, 0, 20).content()).isEmpty();
    }

    // ===== 동시 요청 =====

    /**
     * 장바구니가 없는 회원이 처음 담기를 동시에 두 번 보내는 경우 (버튼 두 번 누르기, 두 옵션을 연달아 담기).
     * 한쪽이 장바구니를 만드는 동안 다른 쪽이 "장바구니 없음"으로 실패하면 안 된다. 타이밍 문제라 여러 회원으로 반복한다.
     */
    @Test
    void 처음_담기를_동시에_두_번_보내도_둘_다_성공한다() throws Exception {
        int rounds = 15;
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            long newBuyer = db.user("first" + i, "USER");
            List<String> outcomes = runTogether(List.of(
                    () -> addOutcome(newBuyer, teeBlack),
                    () -> addOutcome(newBuyer, hoodBlack)));
            outcomes.stream().filter(o -> !"OK".equals(o)).forEach(failures::add);
            if (outcomes.stream().allMatch("OK"::equals)) {
                assertThat(cartService.getCart(newBuyer).items()).extracting("optionId")
                        .containsExactlyInAnyOrder(teeBlack, hoodBlack);
            }
        }
        assertThat(failures).as("실패한 담기 (총 %d번 중)", rounds * 2).isEmpty();
    }

    /**
     * 체크 변경과 같은 옵션 담기가 겹치는 경우: 체크 변경이 읽어 둔 옛 수량으로 덮어쓰면 담기한 수량이 사라진다.
     * 체크 변경 트랜잭션이 항목을 읽은 뒤 멈춘 사이에 다른 트랜잭션이 담기를 커밋하게 해서 순서를 고정한다.
     */
    @Test
    void 체크를_바꾸는_사이_같은_옵션을_더_담아도_늘어난_수량이_사라지지_않는다() throws Exception {
        long itemId = cartService.add(buyer, new CartItemAddRequest(teeBlack, 1)).items().get(0).cartItemId();

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            CartItem item = cartItemRepository.findByIdAndUserId(itemId, buyer).orElseThrow(); // 수량 1 을 읽음
            inAnotherThread(() -> cartService.add(buyer, new CartItemAddRequest(teeBlack, 2))); // 그 사이 3 으로 커밋
            item.changeSelected(false); // 체크만 바꾼다
        });

        CartResponse cart = cartService.getCart(buyer);
        assertThat(cart.items().get(0).selected()).isFalse();
        assertThat(cart.items().get(0).quantity()).isEqualTo(3); // 1 로 덮어써지면 안 된다
    }

    // ===== helpers =====

    private String addOutcome(long buyerId, long optionId) {
        try {
            cartService.add(buyerId, new CartItemAddRequest(optionId, 1));
            return "OK";
        } catch (BusinessException e) {
            return e.getErrorCode().name();
        } catch (Exception e) {
            return e.getClass().getSimpleName();
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

    /** 다른 스레드(= 다른 DB 연결, 다른 트랜잭션)에서 실행하고 커밋될 때까지 기다린다 */
    private void inAnotherThread(Runnable work) {
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            other.submit(work).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            other.shutdown();
        }
    }

    private void assertError(ErrorCode expected, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private int stockOf(long optionId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM product_options WHERE id = ?", Integer.class, optionId);
    }

    private long brandOf(long productId) {
        return jdbc.queryForObject("SELECT brand_id FROM products WHERE id = ?", Long.class, productId);
    }
}
