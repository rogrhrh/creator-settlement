package com.ahn.settlement.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class KstDateRangeTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Test
    void yearMonth_start는_해당월_1일_0시_KST() {
        KstDateRange range = KstDateRange.of(YearMonth.of(2025, 3));
        assertThat(range.start()).isEqualTo(OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST));
    }

    @Test
    void yearMonth_end는_다음달_1일_0시_KST() {
        KstDateRange range = KstDateRange.of(YearMonth.of(2025, 3));
        assertThat(range.end()).isEqualTo(OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST));
    }

    @Test
    void yearMonth_12월_end는_다음해_1월_1일() {
        KstDateRange range = KstDateRange.of(YearMonth.of(2025, 12));
        assertThat(range.end()).isEqualTo(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, KST));
    }

    @Test
    void localDate_start는_당일_0시_KST() {
        KstDateRange range = KstDateRange.of(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));
        assertThat(range.start()).isEqualTo(OffsetDateTime.of(2025, 3, 1, 0, 0, 0, 0, KST));
    }

    @Test
    void localDate_end는_종료일_다음날_0시_KST() {
        KstDateRange range = KstDateRange.of(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));
        assertThat(range.end()).isEqualTo(OffsetDateTime.of(2025, 4, 1, 0, 0, 0, 0, KST));
    }

    @Test
    void 월_경계값_1월말_판매는_1월에_귀속() {
        KstDateRange jan = KstDateRange.of(YearMonth.of(2025, 1));
        OffsetDateTime sale5PaidAt = OffsetDateTime.of(2025, 1, 31, 23, 30, 0, 0, KST);
        assertThat(sale5PaidAt.isBefore(jan.end())).isTrue();
        assertThat(!sale5PaidAt.isBefore(jan.start())).isTrue();
    }

    @Test
    void 월_경계값_2월_취소는_2월에_귀속() {
        KstDateRange feb = KstDateRange.of(YearMonth.of(2025, 2));
        OffsetDateTime cancel3At = OffsetDateTime.of(2025, 2, 1, 0, 30, 0, 0, KST);
        assertThat(cancel3At.isBefore(feb.end())).isTrue();
        assertThat(!cancel3At.isBefore(feb.start())).isTrue();
    }
}
