package com.ssu.shop.db;

import static com.ssu.shop.support.DbFixtures.BLACK;
import static com.ssu.shop.support.DbFixtures.CLOTHING_LEAF;
import static com.ssu.shop.support.DbFixtures.SIZE_M;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandCreateRequest;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandStatusChangeRequest;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandWithProductRequest;
import com.ssu.shop.domain.brand.dto.BrandDtos.BrandWithProductResponse;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import com.ssu.shop.domain.brand.repository.BrandRepository;
import com.ssu.shop.domain.brand.service.BrandService;
import com.ssu.shop.domain.cart.dto.CartDtos.CartItemAddRequest;
import com.ssu.shop.domain.cart.service.CartService;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderCreateRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.OrderResponse;
import com.ssu.shop.domain.order.dto.OrderDtos.PaymentRequest;
import com.ssu.shop.domain.order.dto.OrderDtos.SellerShipmentResponse;
import com.ssu.shop.domain.order.dto.OrderDtos.ShipmentResponse;
import com.ssu.shop.domain.order.entity.CancelActor;
import com.ssu.shop.domain.order.entity.ShipmentStatus;
import com.ssu.shop.domain.order.service.OrderService;
import com.ssu.shop.domain.order.service.SellerShipmentService;
import com.ssu.shop.domain.payment.entity.PaymentMethod;
import com.ssu.shop.domain.product.dto.ProductDtos.AdminProductStatusRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.InitialProductRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.OptionRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductSummaryResponse;
import com.ssu.shop.domain.product.entity.Gender;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.service.ProductService;
import com.ssu.shop.domain.stats.dto.StatsDtos.BrandSales;
import com.ssu.shop.domain.stats.dto.StatsDtos.BrandSalesResponse;
import com.ssu.shop.domain.stats.dto.StatsPeriod;
import com.ssu.shop.domain.stats.service.StatsService;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.DbFixtures;
import java.time.LocalDate;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 프론트 점검(2026-11-20)에서 나온 백엔드 작업을 실제 MySQL 에서 확인한다:
 * 판매자 취소(V8), 브랜드 삭제(V8), 브랜드 + 첫 상품 동시 등록, 브랜드별 매출, 판매자·관리자 목록의 옵션.
 * 실행: DB_TEST_URL 이 있을 때만. 실제 커밋이 필요해 @Transactional 을 쓰지 않고, 전용 이름(seller-ops-test-*)만 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class SellerOpsDbTest {

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
            new PaymentRequest(PaymentMethod.EASY_PAY, null));

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CartService cartService;
    @Autowired
    OrderService orderService;
    @Autowired
    SellerShipmentService sellerShipmentService;
    @Autowired
    BrandService brandService;
    @Autowired
    BrandRepository brandRepository;
    @Autowired
    ProductService productService;
    @Autowired
    StatsService statsService;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    DbFixtures db;
    long buyer;
    long sellerUserA;
    long sellerUserB;
    long brandA;
    long tee;        // 판매자 A, 25,000원
    long teeOption;
    long capOption;  // 판매자 B, 18,000원

    @BeforeEach
    void setUp() {
        db = new DbFixtures(jdbc, "seller-ops-test");
        db.cleanUp();
        buyer = db.user("buyer", "USER");
        sellerUserA = db.user("seller-a", "SELLER");
        sellerUserB = db.user("seller-b", "SELLER");
        long sellerA = db.seller(sellerUserA, "4444444401");
        long sellerB = db.seller(sellerUserB, "4444444402");
        brandA = db.brand(sellerA, "A");
        tee = db.product(brandA, "SO-1", 25_000, "ON_SALE");
        teeOption = db.option(tee, BLACK, 10);
        capOption = db.option(db.product(db.brand(sellerB, "B"), "SO-2", 18_000, "ON_SALE"), BLACK, 10);
    }

    @AfterEach
    void tearDown() {
        db.cleanUp();
    }

    // ===== 도우미 =====

    private OrderResponse order(long... optionQtyPairs) {
        for (int i = 0; i < optionQtyPairs.length; i += 2) {
            cartService.add(buyer, new CartItemAddRequest(optionQtyPairs[i], (int) optionQtyPairs[i + 1]));
        }
        return orderService.create(buyer, ORDER);
    }

    private static long shipmentOf(OrderResponse order, long subtotal) {
        return order.shipments().stream().filter(s -> s.subtotal() == subtotal).findFirst().orElseThrow().shipmentId();
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

    private String login(String name) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"seller-ops-test-" + name + "@test.com\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).at("/data/accessToken").asText();
    }

    // ===== 판매자 취소 =====

    @Test
    void 판매자는_배송준비까지_취소할_수_있고_재고와_결제가_돌아오며_구매자에게_사유가_보인다() {
        OrderResponse order = order(teeOption, 2, capOption, 1);
        long shipmentA = shipmentOf(order, 50_000);
        sellerShipmentService.changeStatus(sellerUserA, shipmentA, ShipmentStatus.PREPARING);

        SellerShipmentResponse canceled = sellerShipmentService.cancel(sellerUserA, shipmentA, "  품절로 출고 불가 ");

        assertThat(canceled.status()).isEqualTo(ShipmentStatus.CANCELED);
        assertThat(canceled.canceledBy()).isEqualTo(CancelActor.SELLER);
        assertThat(canceled.cancelReason()).isEqualTo("품절로 출고 불가");
        assertThat(stock(teeOption)).isEqualTo(10);
        OrderResponse buyerView = orderService.getMyOrder(buyer, order.orderId());
        ShipmentResponse a = buyerView.shipments().stream().filter(s -> s.shipmentId() == shipmentA).findFirst()
                .orElseThrow();
        assertThat(a.canceledBy()).isEqualTo(CancelActor.SELLER);
        assertThat(a.cancelReason()).isEqualTo("품절로 출고 불가");
        assertThat(buyerView.payment().refundedAmount()).isEqualTo(50_000);
        assertThat(buyerView.payment().balance()).isEqualTo(18_000);
    }

    @Test
    void 배송중부터는_판매자도_취소할_수_없고_남의_배송_묶음은_없는_것처럼_404() {
        OrderResponse order = order(teeOption, 1);
        long shipmentA = order.shipments().get(0).shipmentId();
        sellerShipmentService.changeStatus(sellerUserA, shipmentA, ShipmentStatus.PREPARING);
        sellerShipmentService.changeStatus(sellerUserA, shipmentA, ShipmentStatus.SHIPPING);

        assertThat(errorOf(() -> sellerShipmentService.cancel(sellerUserA, shipmentA, "출고 불가")))
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
        assertThat(errorOf(() -> sellerShipmentService.cancel(sellerUserB, shipmentA, "출고 불가")))
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(stock(teeOption)).isEqualTo(9);
        assertThat(count("SELECT COUNT(*) FROM payment_refunds WHERE shipment_id = ?", shipmentA)).isZero();
    }

    @Test
    void 구매자_취소는_사유가_선택이고_구매자_취소로_기록된다() {
        OrderResponse withReason = order(teeOption, 1);
        orderService.cancel(buyer, withReason.orderId(), withReason.shipments().get(0).shipmentId(), "단순 변심");
        OrderResponse withoutReason = order(capOption, 1);
        orderService.cancel(buyer, withoutReason.orderId(), withoutReason.shipments().get(0).shipmentId(), "  ");

        assertThat(jdbc.queryForMap("SELECT canceled_by, cancel_reason FROM shipments WHERE order_id = ?",
                withReason.orderId())).containsEntry("canceled_by", "BUYER").containsEntry("cancel_reason", "단순 변심");
        assertThat(jdbc.queryForMap("SELECT canceled_by, cancel_reason FROM shipments WHERE order_id = ?",
                withoutReason.orderId())).containsEntry("canceled_by", "BUYER").containsEntry("cancel_reason", null);
    }

    @Test
    void 구매자와_판매자가_동시에_취소해도_재고_복원과_환불은_한_번이다() throws Exception {
        for (int round = 0; round < 5; round++) {
            OrderResponse order = order(teeOption, 1);
            long shipmentId = order.shipments().get(0).shipmentId();

            List<String> outcomes = runTogether(List.of(
                    () -> outcomeOf(() -> orderService.cancel(buyer, order.orderId(), shipmentId, null)),
                    () -> outcomeOf(() -> sellerShipmentService.cancel(sellerUserA, shipmentId, "출고 불가"))));

            assertThat(outcomes).as("round %d", round)
                    .containsExactlyInAnyOrder("OK", ErrorCode.INVALID_ORDER_STATUS.name());
            assertThat(count("SELECT COUNT(*) FROM payment_refunds WHERE shipment_id = ?", shipmentId)).isEqualTo(1);
            assertThat(stock(teeOption)).as("round %d", round).isEqualTo(10);
        }
    }

    @Test
    void 판매자_취소는_사유가_없으면_400() throws Exception {
        OrderResponse order = order(teeOption, 1);
        long shipmentId = order.shipments().get(0).shipmentId();
        mockMvc.perform(post("/api/seller/shipments/" + shipmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, login("seller-a"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(post("/api/seller/shipments/" + shipmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, login("seller-a"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"품절\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canceledBy").value("SELLER"));
    }

    // ===== 브랜드 삭제 =====

    @Test
    void 브랜드를_삭제하면_상품이_판매_종료되고_목록에서_빠지며_같은_이름을_다시_쓸_수_있다() {
        String originalName = jdbc.queryForObject("SELECT name FROM brands WHERE id = ?", String.class, brandA);

        brandService.deleteMine(sellerUserA, brandA);

        assertThat(jdbc.queryForMap("SELECT name, status, deleted_at FROM brands WHERE id = ?", brandA))
                .containsEntry("name", "deleted_" + brandA + "_" + originalName)
                .containsEntry("status", "DELETED")
                .extractingByKey("deleted_at").isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, tee))
                .isEqualTo("DELETED");
        assertThat(brandService.myBrands(sellerUserA)).isEmpty();
        assertThat(brandService.search(null, PageRequest.of(0, 100)).content())
                .noneMatch(b -> b.id().equals(brandA));
        assertThat(brandService.search(BrandStatus.DELETED, PageRequest.of(0, 100)).content())
                .anyMatch(b -> b.id().equals(brandA));
        // 같은 이름으로 새 브랜드 가능
        assertThat(brandService.create(sellerUserA, new BrandCreateRequest(originalName, null, null)).name())
                .isEqualTo(originalName);
        // 이미 삭제한 브랜드는 없는 것처럼
        assertThat(errorOf(() -> brandService.deleteByAdmin(brandA))).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 진행_중인_주문이_있으면_브랜드를_삭제할_수_없고_배송완료_후에는_관리자도_삭제할_수_있다() {
        OrderResponse order = order(teeOption, 1);
        long shipmentId = order.shipments().get(0).shipmentId();

        assertThatThrownBy(() -> brandService.deleteMine(sellerUserA, brandA))
                .hasMessageContaining("1건")
                .extracting("errorCode").isEqualTo(ErrorCode.HAS_IN_PROGRESS_ORDERS);
        assertThat(errorOf(() -> brandService.deleteByAdmin(brandA))).isEqualTo(ErrorCode.HAS_IN_PROGRESS_ORDERS);
        // 거절되면 상품 판매 종료까지 롤백
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, tee))
                .isEqualTo("ON_SALE");

        for (ShipmentStatus next : List.of(ShipmentStatus.PREPARING, ShipmentStatus.SHIPPING, ShipmentStatus.DELIVERED)) {
            sellerShipmentService.changeStatus(sellerUserA, shipmentId, next);
        }
        assertThat(brandService.deleteByAdmin(brandA).status()).isEqualTo(BrandStatus.DELETED);
    }

    @Test
    void 남의_브랜드는_삭제할_수_없다() {
        assertThat(errorOf(() -> brandService.deleteMine(sellerUserB, brandA))).isEqualTo(ErrorCode.BRAND_NOT_OWNED);
        assertThat(jdbc.queryForObject("SELECT status FROM brands WHERE id = ?", String.class, brandA))
                .isEqualTo("APPROVED");
    }

    @Test
    void 브랜드를_삭제하면_그_브랜드로_상품을_등록할_수_없다() {
        brandService.deleteMine(sellerUserA, brandA);
        assertThat(errorOf(() -> productService.create(sellerUserA,
                initialProduct("X-1", SIZE_M).toCreateRequest(brandA)))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(count("SELECT COUNT(*) FROM products WHERE brand_id = ? AND status <> 'DELETED'", brandA)).isZero();
    }

    // ===== 브랜드 + 첫 상품 =====

    private static InitialProductRequest initialProduct(String code, long sizeId) {
        return new InitialProductRequest(CLOTHING_LEAF, code, "첫 상품 " + code, null, 30_000, Gender.UNISEX, "24FW", 3,
                null, List.of(new OptionRequest(BLACK, sizeId, 5)));
    }

    @Test
    void 브랜드와_첫_상품을_한_번에_등록하고_브랜드_승인_뒤에_상품을_승인한다() {
        BrandWithProductResponse created = brandService.createWithProduct(sellerUserA, new BrandWithProductRequest(
                new BrandCreateRequest("SELLER-OPS-TEST-NEW", "새 브랜드", null), initialProduct("NEW-1", SIZE_M)));
        long brandId = created.brand().id();
        long productId = created.product().id();
        assertThat(created.brand().status()).isEqualTo(BrandStatus.PENDING);
        assertThat(created.product().status()).isEqualTo(ProductStatus.PENDING);
        assertThat(created.product().options()).singleElement().satisfies(o -> assertThat(o.stock()).isEqualTo(5));

        assertThat(errorOf(() -> productService.adminChangeStatus(productId,
                new AdminProductStatusRequest(ProductStatus.ON_SALE, null)))).isEqualTo(ErrorCode.BRAND_NOT_APPROVED);

        brandService.changeStatus(brandId, new BrandStatusChangeRequest(BrandStatus.APPROVED, null));
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, productId))
                .isEqualTo("PENDING"); // 브랜드 승인만으로는 판매되지 않는다 (상품은 따로 승인)
        assertThat(productService.adminChangeStatus(productId,
                new AdminProductStatusRequest(ProductStatus.ON_SALE, null)).status()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 첫_상품이_잘못되면_브랜드도_만들어지지_않는다() {
        // 신발 사이즈(id 11)는 의류 카테고리에 쓸 수 없다 → INVALID_OPTION
        assertThat(errorOf(() -> brandService.createWithProduct(sellerUserA, new BrandWithProductRequest(
                new BrandCreateRequest("SELLER-OPS-TEST-BAD", null, null), initialProduct("BAD-1", 11L)))))
                .isEqualTo(ErrorCode.INVALID_OPTION);
        assertThat(brandRepository.existsByName("SELLER-OPS-TEST-BAD")).isFalse();
    }

    // ===== 브랜드별 매출 =====

    @Test
    void 브랜드별_매출은_취소를_빼고_합계가_플랫폼_합계와_같다() {
        order(teeOption, 2, capOption, 1);                 // A 50,000 · B 18,000
        OrderResponse canceled = order(capOption, 3);      // 취소 → 제외
        orderService.cancel(buyer, canceled.orderId(), canceled.shipments().get(0).shipmentId(), null);
        db.brand(jdbc.queryForObject("SELECT seller_id FROM brands WHERE id = ?", Long.class, brandA), "C"); // 판매 없음
        LocalDate today = LocalDate.now();

        BrandSalesResponse result = statsService.brandSales(today, today);

        BrandSales a = result.brands().stream().filter(b -> b.brandId() == brandA).findFirst().orElseThrow();
        assertThat(a.revenue()).isEqualTo(50_000);
        assertThat(a.quantity()).isEqualTo(2);
        assertThat(a.orderCount()).isEqualTo(1);
        assertThat(a.productCount()).isEqualTo(1);
        BrandSales b = result.brands().stream().filter(x -> x.brandName().equals("SELLER-OPS-TEST-B")).findFirst()
                .orElseThrow();
        assertThat(b.revenue()).isEqualTo(18_000);
        assertThat(result.brands()).anyMatch(x -> x.brandName().equals("SELLER-OPS-TEST-C") && x.revenue() == 0);
        assertThat(result.totalRevenue())
                .isEqualTo(statsService.platformSales(StatsPeriod.DAILY, today, today).totalRevenue());
    }

    // ===== 목록의 옵션 =====

    @Test
    void 판매자와_관리자_상품_목록에는_옵션이_담기고_구매자_목록에는_빠진다() throws Exception {
        ProductSummaryResponse mine = productService.myProducts(sellerUserA, null, 0, 20).content().stream()
                .filter(p -> p.id() == tee).findFirst().orElseThrow();
        assertThat(mine.options()).singleElement().satisfies(o -> {
            assertThat(o.colorName()).isNotBlank();
            assertThat(o.stock()).isEqualTo(10);
        });
        assertThat(productService.adminSearch(ProductStatus.ON_SALE, "SO-1", 0, 20).content())
                .allSatisfy(p -> assertThat(p.options()).isNotNull());

        mockMvc.perform(get("/api/products").param("keyword", "SO-1")
                        .header(HttpHeaders.AUTHORIZATION, login("buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(tee))
                .andExpect(jsonPath("$.data.content[0].options").doesNotExist());
    }

    // ===== V8 제약 =====

    @Test
    void 판매자_취소는_사유_없이_저장할_수_없고_삭제한_브랜드는_삭제_시각이_필요하다() {
        OrderResponse order = order(teeOption, 1);
        long shipmentId = order.shipments().get(0).shipmentId();
        assertThatThrownBy(() -> jdbc.update("UPDATE shipments SET status = 'CANCELED', canceled_at = NOW(6), "
                + "canceled_by = 'SELLER' WHERE id = ?", shipmentId))
                .hasMessageContaining("ck_shipments_seller_reason");
        assertThatThrownBy(() -> jdbc.update("UPDATE shipments SET status = 'CANCELED', canceled_at = NOW(6) "
                + "WHERE id = ?", shipmentId))
                .hasMessageContaining("ck_shipments_canceled_by");
        assertThatThrownBy(() -> jdbc.update("UPDATE brands SET status = 'DELETED' WHERE id = ?", brandA))
                .hasMessageContaining("ck_brands_deleted");
    }

    // ===== 동시 실행 도우미 =====

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
