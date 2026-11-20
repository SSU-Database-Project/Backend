package com.ssu.shop.domain.stats.dto;

import java.time.LocalDate;
import java.util.List;

public final class StatsDtos {

    private StatsDtos() {
    }

    /** 구간 하나: 판매량(개), 매출(원), 주문 수. 판매가 없던 구간도 0 으로 채워서 내려준다(차트 x축이 끊기지 않게) */
    public record SalesPoint(LocalDate periodStart, long quantity, long revenue, long orderCount) {
    }

    public record SalesStatsResponse(
            StatsPeriod period,
            LocalDate from,
            LocalDate to,
            long totalQuantity,
            long totalRevenue,
            long totalOrders,
            List<SalesPoint> points) {
    }

    public record TopProduct(int rank, Long productId, String productCode, String productName,
                             long quantity, long revenue) {
    }

    public record TopProductsResponse(LocalDate from, LocalDate to, String sort, List<TopProduct> products) {
    }

    /**
     * 브랜드별 판매 (관리자, V8). 판매·날짜 기준은 다른 통계와 같다 (취소 제외, 주문 시각).
     * productCount = 삭제되지 않은 상품 수. 승인된 브랜드는 판매가 없어도 0 으로 나오고, 기간 안에 판매가 있던 브랜드는
     * 지금 상태(거절·삭제 포함)와 상관없이 나온다 → 브랜드 합계 = 플랫폼 합계.
     */
    public record BrandSales(Long brandId, String brandName, String brandStatus, Long sellerId,
                             String sellerCompanyName, long productCount, long quantity, long revenue,
                             long orderCount) {
    }

    public record BrandSalesResponse(LocalDate from, LocalDate to, long totalQuantity, long totalRevenue,
                                     List<BrandSales> brands) {
    }

    public record Figure(long quantity, long revenue, long orderCount) {
    }

    /** 판매자 대시보드: 오늘 / 이번 주 / 이번 달 + 처리해야 할 배송 수 */
    public record SellerSummaryResponse(
            LocalDate today,
            Figure todaySales,
            Figure thisWeekSales,
            Figure thisMonthSales,
            long toPrepare,   // ORDERED: 주문 들어옴, 배송준비로 넘겨야 함
            long preparing,   // PREPARING
            long shipping) {  // SHIPPING
    }
}
