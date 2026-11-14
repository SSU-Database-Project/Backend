package com.ssu.shop.domain.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.domain.stats.dto.StatsDtos.SalesPoint;
import com.ssu.shop.domain.stats.dto.StatsPeriod;
import com.ssu.shop.domain.stats.repository.StatsRepository;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.Fixtures;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    StatsRepository statsRepository;
    @Mock
    SellerService sellerService;

    StatsService statsService;

    // 2026-11-11(수) 10:00 KST
    static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(2026, 11, 11, 10, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @BeforeEach
    void setUp() {
        statsService = new StatsService(statsRepository, sellerService, CLOCK);
    }

    @Test
    void 기간을_생략하면_일별은_오늘까지_최근_30일() {
        var r = statsService.resolve(StatsPeriod.DAILY, null, null);
        assertThat(r.to()).isEqualTo(TODAY);
        assertThat(r.from()).isEqualTo(TODAY.minusDays(29));
    }

    @Test
    void 주별은_시작일을_월요일로_월별은_1일로_맞춘다() {
        assertThat(statsService.resolve(StatsPeriod.WEEKLY, LocalDate.of(2026, 10, 15), TODAY).from())
                .isEqualTo(LocalDate.of(2026, 10, 12)); // 10/15(목) → 그 주 월요일
        assertThat(statsService.resolve(StatsPeriod.MONTHLY, LocalDate.of(2026, 9, 20), TODAY).from())
                .isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void 판매가_없던_날도_0으로_채운다() {
        when(sellerService.getSeller(100L)).thenReturn(Fixtures.seller(1L, Fixtures.user(100L, "s@test.com")));
        LocalDate from = LocalDate.of(2026, 11, 1);
        when(statsRepository.sellerSales(eq(1L), eq(StatsPeriod.DAILY), eq(from), eq(LocalDate.of(2026, 11, 5))))
                .thenReturn(List.of(new SalesPoint(LocalDate.of(2026, 11, 2), 3, 30_000, 2),
                        new SalesPoint(LocalDate.of(2026, 11, 5), 1, 10_000, 1)));

        var res = statsService.sellerSales(100L, StatsPeriod.DAILY, from, LocalDate.of(2026, 11, 5));

        assertThat(res.points()).hasSize(5);
        assertThat(res.points()).extracting(SalesPoint::quantity).containsExactly(0L, 3L, 0L, 0L, 1L);
        assertThat(res.totalQuantity()).isEqualTo(4);
        assertThat(res.totalRevenue()).isEqualTo(40_000);
        assertThat(res.totalOrders()).isEqualTo(3);
    }

    @Test
    void from이_to보다_뒤이면_400() {
        assertThatThrownBy(() -> statsService.resolve(StatsPeriod.DAILY, TODAY, TODAY.minusDays(1)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 일별은_1년이_넘는_기간을_한번에_조회할_수_없다() {
        assertThatThrownBy(() -> statsService.resolve(StatsPeriod.DAILY, TODAY.minusDays(400), TODAY))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 순위_정렬은_quantity_revenue만_허용한다() {
        assertThatThrownBy(() -> statsService.sellerTopProducts(100L, null, null, "price", 10))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 대시보드는_오늘_이번주_월요일부터_이번달_1일부터() {
        when(sellerService.getSeller(100L)).thenReturn(Fixtures.seller(1L, Fixtures.user(100L, "s@test.com")));
        when(statsRepository.sellerFigure(eq(1L), any(), any()))
                .thenReturn(new com.ssu.shop.domain.stats.dto.StatsDtos.Figure(0, 0, 0));
        when(statsRepository.sellerOpenShipments(1L)).thenReturn(java.util.Map.of("ORDERED", 2L));

        var res = statsService.sellerSummary(100L);

        verify(statsRepository).sellerFigure(1L, TODAY, TODAY);
        verify(statsRepository).sellerFigure(1L, LocalDate.of(2026, 11, 9), TODAY); // 월요일
        verify(statsRepository).sellerFigure(1L, LocalDate.of(2026, 11, 1), TODAY);
        assertThat(res.toPrepare()).isEqualTo(2);
        assertThat(res.preparing()).isZero();
    }
}
