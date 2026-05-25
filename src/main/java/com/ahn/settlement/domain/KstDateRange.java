package com.ahn.settlement.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

public record KstDateRange(OffsetDateTime start, OffsetDateTime end) {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    public static KstDateRange of(YearMonth yearMonth) {
        OffsetDateTime start = yearMonth.atDay(1).atStartOfDay().atOffset(KST);
        OffsetDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay().atOffset(KST);
        return new KstDateRange(start, end);
    }

    public static KstDateRange of(LocalDate startDate, LocalDate endDate) {
        OffsetDateTime start = startDate.atStartOfDay().atOffset(KST);
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay().atOffset(KST);
        return new KstDateRange(start, end);
    }
}
