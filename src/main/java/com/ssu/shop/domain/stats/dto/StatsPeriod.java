package com.ssu.shop.domain.stats.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 통계 집계 단위. SQL 의 구간 시작일 식은 이 enum 안에서만 정해진다(사용자 입력을 SQL 에 넣지 않음).
 * 주는 월요일 시작(ISO), 월은 1일 시작.
 */
@Getter
@RequiredArgsConstructor
public enum StatsPeriod {
    DAILY("DATE(%s)", 30, 366),
    WEEKLY("DATE(%s) - INTERVAL WEEKDAY(%s) DAY", 12 * 7, 366 * 2),
    MONTHLY("CAST(DATE_FORMAT(%s, '%%Y-%%m-01') AS DATE)", 365, 366 * 5);

    /** %s = 시각 컬럼. 결과는 구간 시작일(DATE) */
    private final String bucketSqlTemplate;
    /** from/to 를 생략했을 때 기본 조회 기간(일) */
    private final int defaultDays;
    /** 한 번에 조회할 수 있는 최대 기간(일) — 너무 넓은 범위로 큰 집계가 도는 것을 막는다 */
    private final int maxDays;

    public String bucketSql(String column) {
        return bucketSqlTemplate.replace("%s", column).replace("%%", "%");
    }

    /** 날짜가 속한 구간의 시작일 */
    public LocalDate bucketStart(LocalDate date) {
        return switch (this) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> date.withDayOfMonth(1);
        };
    }

    public LocalDate next(LocalDate bucketStart) {
        return switch (this) {
            case DAILY -> bucketStart.plusDays(1);
            case WEEKLY -> bucketStart.plusWeeks(1);
            case MONTHLY -> bucketStart.plusMonths(1);
        };
    }

    public boolean exceeds(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to) + 1 > maxDays;
    }
}
