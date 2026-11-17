package com.ssu.shop.domain.stats.controller;

import com.ssu.shop.domain.stats.dto.StatsDtos.SalesStatsResponse;
import com.ssu.shop.domain.stats.dto.StatsDtos.SellerSummaryResponse;
import com.ssu.shop.domain.stats.dto.StatsDtos.TopProductsResponse;
import com.ssu.shop.domain.stats.dto.StatsPeriod;
import com.ssu.shop.domain.stats.service.StatsService;
import com.ssu.shop.global.common.ApiResponse;
import com.ssu.shop.global.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller - Stats", description = "판매 통계 (취소 제외, 주문일 기준)")
@RestController
@RequestMapping("/api/seller/stats")
@RequiredArgsConstructor
public class SellerStatsController {

    private final StatsService statsService;

    @Operation(summary = "기간별 판매량·매출",
            description = "period = DAILY | WEEKLY(월요일 시작) | MONTHLY. from/to 생략 시 최근 30일 / 12주 / 12개월. "
                    + "판매가 없던 구간도 0으로 채워 내려준다")
    @GetMapping("/sales")
    public ApiResponse<SalesStatsResponse> sales(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "DAILY") StatsPeriod period,
            @Parameter(description = "yyyy-MM-dd") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "yyyy-MM-dd") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(statsService.sellerSales(loginUser.id(), period, from, to));
    }

    @Operation(summary = "상품별 판매 순위", description = "sort = quantity(판매량, 기본) | revenue(매출). 기간 생략 시 최근 30일")
    @GetMapping("/products/top")
    public ApiResponse<TopProductsResponse> topProducts(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "quantity") String sort,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ApiResponse.success(statsService.sellerTopProducts(loginUser.id(), from, to, sort, limit));
    }

    @Operation(summary = "대시보드 요약", description = "오늘 / 이번 주 / 이번 달 판매량·매출 + 처리할 배송 수(주문완료·배송준비·배송중)")
    @GetMapping("/summary")
    public ApiResponse<SellerSummaryResponse> summary(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(statsService.sellerSummary(loginUser.id()));
    }
}
