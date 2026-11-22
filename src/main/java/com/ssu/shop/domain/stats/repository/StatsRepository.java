package com.ssu.shop.domain.stats.repository;

import com.ssu.shop.domain.stats.dto.StatsDtos.BrandSales;
import com.ssu.shop.domain.stats.dto.StatsDtos.Figure;
import com.ssu.shop.domain.stats.dto.StatsDtos.SalesPoint;
import com.ssu.shop.domain.stats.dto.StatsDtos.TopProduct;
import com.ssu.shop.domain.stats.dto.StatsPeriod;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 판매 통계 집계 쿼리 (네이티브 SQL). 기간별 GROUP BY 는 DBMS 날짜 함수(DATE, WEEKDAY, DATE_FORMAT)를 써야 해서 JPQL 대신 SQL 로 쓴다.
 *
 * 집계 기준 (팀 확정 전, BE-1 제안):
 *  - 판매 = 취소(CANCELED)가 아닌 배송 묶음의 주문 라인. 배송 상태(주문완료~구매확정)와 상관없이 주문 시점에 판매로 본다.
 *  - 날짜 = 주문 시각. 판매자 통계는 shipments.created_at(주문과 같은 트랜잭션에서 생성 = 주문 시각)을 쓴다.
 *    그래야 idx_shipments_seller_created (seller_id, created_at) 범위 스캔 하나로 판매자 + 기간을 함께 좁힌다.
 *  - 매출 = Σ 주문 당시 단가(order_items.unit_price 스냅샷) × 수량. 상품 가격이 나중에 바뀌어도 과거 매출은 그대로다.
 *  - 기간은 [from 00:00, to+1 00:00) 반열린 구간. BETWEEN 을 쓰면 to 당일 23:59:59.xxx 경계를 놓치기 쉽다.
 */
@Repository
@RequiredArgsConstructor
public class StatsRepository {

    private static final String NOT_CANCELED = "sh.status <> 'CANCELED'";

    private final NamedParameterJdbcTemplate jdbc;

    /** 판매자 기간별 판매량·매출. shipments(seller, 기간) → order_items */
    public List<SalesPoint> sellerSales(Long sellerId, StatsPeriod period, LocalDate from, LocalDate to) {
        String bucket = period.bucketSql("sh.created_at");
        String sql = """
                SELECT %s AS bucket,
                       SUM(oi.quantity)                 AS qty,
                       SUM(oi.quantity * oi.unit_price) AS revenue,
                       COUNT(DISTINCT sh.order_id)      AS orders
                  FROM shipments sh
                  JOIN order_items oi ON oi.shipment_id = sh.id
                 WHERE sh.seller_id = :sellerId
                   AND sh.created_at >= :from AND sh.created_at < :toExclusive
                   AND %s
                 GROUP BY bucket
                 ORDER BY bucket
                """.formatted(bucket, NOT_CANCELED);
        return jdbc.query(sql, range(from, to).addValue("sellerId", sellerId), (rs, i) -> new SalesPoint(
                rs.getObject("bucket", LocalDate.class), rs.getLong("qty"), rs.getLong("revenue"), rs.getLong("orders")));
    }

    /** 플랫폼 전체 기간별 판매량·매출. orders(기간, idx_orders_created) → shipments → order_items */
    public List<SalesPoint> platformSales(StatsPeriod period, LocalDate from, LocalDate to) {
        String bucket = period.bucketSql("o.created_at");
        String sql = """
                SELECT %s AS bucket,
                       SUM(oi.quantity)                 AS qty,
                       SUM(oi.quantity * oi.unit_price) AS revenue,
                       COUNT(DISTINCT o.id)             AS orders
                  FROM orders o
                  JOIN shipments sh   ON sh.order_id = o.id
                  JOIN order_items oi ON oi.shipment_id = sh.id
                 WHERE o.created_at >= :from AND o.created_at < :toExclusive
                   AND %s
                 GROUP BY bucket
                 ORDER BY bucket
                """.formatted(bucket, NOT_CANCELED);
        return jdbc.query(sql, range(from, to), (rs, i) -> new SalesPoint(
                rs.getObject("bucket", LocalDate.class), rs.getLong("qty"), rs.getLong("revenue"), rs.getLong("orders")));
    }

    /**
     * 상품별 판매 순위. 옵션(색상·사이즈) 단위로 팔린 것을 상품 단위로 합친다.
     * 상품명은 지금 이름(products.name). 주문 당시 이름은 order_items.product_name 에 따로 남아 있다.
     */
    public List<TopProduct> sellerTopProducts(Long sellerId, LocalDate from, LocalDate to, boolean byRevenue,
                                              int limit) {
        String order = byRevenue ? "revenue DESC, qty DESC" : "qty DESC, revenue DESC";
        String sql = """
                SELECT p.id, p.product_code, p.name,
                       SUM(oi.quantity)                 AS qty,
                       SUM(oi.quantity * oi.unit_price) AS revenue
                  FROM shipments sh
                  JOIN order_items oi     ON oi.shipment_id = sh.id
                  JOIN product_options po ON po.id = oi.option_id
                  JOIN products p         ON p.id = po.product_id
                 WHERE sh.seller_id = :sellerId
                   AND sh.created_at >= :from AND sh.created_at < :toExclusive
                   AND %s
                 GROUP BY p.id, p.product_code, p.name
                 ORDER BY %s, p.id
                 LIMIT :limit
                """.formatted(NOT_CANCELED, order);
        int[] rank = {0};
        return jdbc.query(sql, range(from, to).addValue("sellerId", sellerId).addValue("limit", limit),
                (rs, i) -> new TopProduct(++rank[0], rs.getLong("id"), rs.getString("product_code"),
                        rs.getString("name"), rs.getLong("qty"), rs.getLong("revenue")));
    }

    /** 대시보드: [from, to] 기간 합계 한 줄 */
    public Figure sellerFigure(Long sellerId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT COALESCE(SUM(oi.quantity), 0)                 AS qty,
                       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS revenue,
                       COUNT(DISTINCT sh.order_id)                   AS orders
                  FROM shipments sh
                  JOIN order_items oi ON oi.shipment_id = sh.id
                 WHERE sh.seller_id = :sellerId
                   AND sh.created_at >= :from AND sh.created_at < :toExclusive
                   AND %s
                """.formatted(NOT_CANCELED);
        return jdbc.queryForObject(sql, range(from, to).addValue("sellerId", sellerId),
                (rs, i) -> new Figure(rs.getLong("qty"), rs.getLong("revenue"), rs.getLong("orders")));
    }

    /** 대시보드: 판매자가 처리해야 할 배송 묶음 수 (상태별) */
    public Map<String, Long> sellerOpenShipments(Long sellerId) {
        String sql = """
                SELECT sh.status, COUNT(*) AS cnt
                  FROM shipments sh
                 WHERE sh.seller_id = :sellerId AND sh.status IN ('ORDERED', 'PREPARING', 'SHIPPING')
                 GROUP BY sh.status
                """;
        Map<String, Long> result = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("sellerId", sellerId),
                rs -> {
                    result.put(rs.getString("status"), rs.getLong("cnt"));
                });
        return result;
    }

    /**
     * 브랜드별 판매 (V8). 기간 안의 취소되지 않은 배송 묶음 → 주문 라인 → 옵션 → 상품 → 브랜드로 묶는다.
     * orderCount = 그 브랜드 상품이 담긴 서로 다른 주문 수. 매출 순(같으면 판매량 순, 브랜드 id 순).
     */
    public List<BrandSales> brandSales(LocalDate from, LocalDate to) {
        String sql = """
                SELECT b.id, b.name, b.status, s.id AS seller_id, s.company_name,
                       (SELECT COUNT(*) FROM products p2
                         WHERE p2.brand_id = b.id AND p2.status <> 'DELETED') AS product_count,
                       COALESCE(x.qty, 0) AS qty, COALESCE(x.revenue, 0) AS revenue, COALESCE(x.orders, 0) AS orders
                  FROM brands b
                  JOIN sellers s ON s.id = b.seller_id
                  LEFT JOIN (SELECT p.brand_id,
                                    SUM(oi.quantity)                 AS qty,
                                    SUM(oi.quantity * oi.unit_price) AS revenue,
                                    COUNT(DISTINCT sh.order_id)      AS orders
                               FROM shipments sh
                               JOIN order_items oi     ON oi.shipment_id = sh.id
                               JOIN product_options po ON po.id = oi.option_id
                               JOIN products p         ON p.id = po.product_id
                              WHERE sh.created_at >= :from AND sh.created_at < :toExclusive
                                AND %s
                              GROUP BY p.brand_id) x ON x.brand_id = b.id
                 WHERE b.status = 'APPROVED' OR x.brand_id IS NOT NULL
                 ORDER BY revenue DESC, qty DESC, b.id
                """.formatted(NOT_CANCELED);
        return jdbc.query(sql, range(from, to), (rs, i) -> new BrandSales(rs.getLong("id"), rs.getString("name"),
                rs.getString("status"), rs.getLong("seller_id"), rs.getString("company_name"),
                rs.getLong("product_count"), rs.getLong("qty"), rs.getLong("revenue"), rs.getLong("orders")));
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource()
                .addValue("from", from.atStartOfDay())
                .addValue("toExclusive", to.plusDays(1).atStartOfDay());
    }
}
