package com.ssu.shop.domain.stats.service;

import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.domain.stats.dto.StatsDtos.Figure;
import com.ssu.shop.domain.stats.dto.StatsDtos.SalesPoint;
import com.ssu.shop.domain.stats.dto.StatsDtos.SalesStatsResponse;
import com.ssu.shop.domain.stats.dto.StatsDtos.SellerSummaryResponse;
import com.ssu.shop.domain.stats.dto.StatsDtos.TopProductsResponse;
import com.ssu.shop.domain.stats.dto.StatsPeriod;
import com.ssu.shop.domain.stats.repository.StatsRepository;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final StatsRepository statsRepository;
    private final SellerService sellerService;
    private final Clock clock;

    /** 판매자 기간별 판매량·매출 (일/주/월) */
    public SalesStatsResponse sellerSales(Long userId, StatsPeriod period, LocalDate from, LocalDate to) {
        Long sellerId = sellerService.getSeller(userId).getId();
        Range r = resolve(period, from, to);
        return toResponse(period, r, statsRepository.sellerSales(sellerId, period, r.from(), r.to()));
    }

    /** 플랫폼 전체 기간별 판매량·매출 (관리자) */
    public SalesStatsResponse platformSales(StatsPeriod period, LocalDate from, LocalDate to) {
        Range r = resolve(period, from, to);
        return toResponse(period, r, statsRepository.platformSales(period, r.from(), r.to()));
    }

    /** 상품별 판매 순위. sort = quantity(판매량) | revenue(매출) */
    public TopProductsResponse sellerTopProducts(Long userId, LocalDate from, LocalDate to, String sort, int limit) {
        boolean byRevenue = parseSort(sort);
        Long sellerId = sellerService.getSeller(userId).getId();
        Range r = resolve(StatsPeriod.DAILY, from, to);
        return new TopProductsResponse(r.from(), r.to(), byRevenue ? "revenue" : "quantity",
                statsRepository.sellerTopProducts(sellerId, r.from(), r.to(), byRevenue, limit));
    }

    /** 판매자 대시보드: 오늘 / 이번 주(월요일부터) / 이번 달 + 처리할 배송 수 */
    public SellerSummaryResponse sellerSummary(Long userId) {
        Long sellerId = sellerService.getSeller(userId).getId();
        LocalDate today = LocalDate.now(clock);
        Figure todaySales = statsRepository.sellerFigure(sellerId, today, today);
        Figure week = statsRepository.sellerFigure(sellerId,
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today);
        Figure month = statsRepository.sellerFigure(sellerId, today.withDayOfMonth(1), today);
        Map<String, Long> open = statsRepository.sellerOpenShipments(sellerId);
        return new SellerSummaryResponse(today, todaySales, week, month,
                open.getOrDefault("ORDERED", 0L), open.getOrDefault("PREPARING", 0L), open.getOrDefault("SHIPPING", 0L));
    }

    // ===== 내부 =====

    record Range(LocalDate from, LocalDate to) {
    }

    /**
     * 기간 정리: 생략하면 to = 오늘, from = 단위별 기본 기간 전. from 은 구간 시작일(주 = 월요일, 월 = 1일)로 맞춘다.
     * 그래야 첫 구간이 일부 날짜만 세어 작게 보이는 일이 없다.
     */
    Range resolve(StatsPeriod period, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = period.bucketStart(from != null ? from : end.minusDays(period.getDefaultDays() - 1L));
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from 은 to 보다 앞이어야 합니다.");
        }
        if (period.exceeds(start, end)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    period + " 통계는 최대 " + period.getMaxDays() + "일까지 조회할 수 있습니다.");
        }
        return new Range(start, end);
    }

    /** 판매가 없던 구간도 0 으로 채운다 → 차트의 x축이 끊기지 않고, FE 가 빈 날을 계산할 필요가 없다 */
    private SalesStatsResponse toResponse(StatsPeriod period, Range r, List<SalesPoint> rows) {
        Map<LocalDate, SalesPoint> byStart = rows.stream()
                .collect(Collectors.toMap(SalesPoint::periodStart, Function.identity()));
        List<SalesPoint> points = new ArrayList<>();
        for (LocalDate d = r.from(); !d.isAfter(r.to()); d = period.next(d)) {
            points.add(byStart.getOrDefault(d, new SalesPoint(d, 0, 0, 0)));
        }
        long qty = points.stream().mapToLong(SalesPoint::quantity).sum();
        long revenue = points.stream().mapToLong(SalesPoint::revenue).sum();
        long orders = points.stream().mapToLong(SalesPoint::orderCount).sum();
        return new SalesStatsResponse(period, r.from(), r.to(), qty, revenue, orders, points);
    }

    private static boolean parseSort(String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("quantity")) {
            return false;
        }
        if (sort.equalsIgnoreCase("revenue")) {
            return true;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "sort 는 quantity 또는 revenue 여야 합니다.");
    }
}
