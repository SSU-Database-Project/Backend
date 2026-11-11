package com.ssu.shop.domain.stats.controller;

import com.ssu.shop.domain.stats.dto.StatsDtos.SalesStatsResponse;
import com.ssu.shop.domain.stats.dto.StatsPeriod;
import com.ssu.shop.domain.stats.service.StatsService;
import com.ssu.shop.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Stats", description = "플랫폼 전체 판매 통계 (취소 제외, 주문일 기준)")
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final StatsService statsService;

    @Operation(summary = "플랫폼 전체 기간별 판매량·매출", description = "period = DAILY | WEEKLY | MONTHLY, 판매자 통계와 같은 규칙")
    @GetMapping("/sales")
    public ApiResponse<SalesStatsResponse> sales(
            @RequestParam(defaultValue = "DAILY") StatsPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(statsService.platformSales(period, from, to));
    }
}
