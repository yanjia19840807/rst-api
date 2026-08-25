package com.cmacgm.gbs.rst.api.exercise.associateddata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DailyMonthlyVolumeMathTests {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final UUID EXERCISE = UUID.fromString("d03a4c9a-50f2-4caf-b4e8-e1886af53eba");

    @Test
    void emptySidesPassAsInfo() {
        DailyMonthlyVolumeMath.Result result = DailyMonthlyVolumeMath.compare(List.of(), List.of());
        assertThat(result.passed()).isTrue();
        assertThat(result.reason()).isEqualTo(DailyMonthlyVolumeMath.REASON_BOTH_EMPTY);
        assertThat(result.comparedMonths()).isZero();
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    void overlappingMonthMustMatch() {
        DailyMonthlyVolumeMath.Result result = DailyMonthlyVolumeMath.compare(
                List.of(monthly("2026-01-01", "100")),
                List.of(daily("2026-01-10", "40"), daily("2026-01-20", "60")));
        assertThat(result.passed()).isTrue();
        assertThat(result.reason()).isEqualTo(DailyMonthlyVolumeMath.REASON_MATCHED);
        assertThat(result.comparedMonths()).isEqualTo(1);
        assertThat(result.comparable()).isTrue();
    }

    @Test
    void mismatchIsWarning() {
        DailyMonthlyVolumeMath.Result result = DailyMonthlyVolumeMath.compare(
                List.of(monthly("2026-01-01", "100")),
                List.of(daily("2026-01-10", "40"), daily("2026-01-20", "50")));
        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).isEqualTo(DailyMonthlyVolumeMath.REASON_MISMATCH);
        assertThat(result.mismatches()).containsExactly(
                new DailyMonthlyVolumeMath.MonthMismatch("2026-01", "90", "100"));
    }

    @Test
    void monthsWithoutTheOtherSideAreIgnored() {
        DailyMonthlyVolumeMath.Result result = DailyMonthlyVolumeMath.compare(
                List.of(monthly("2026-01-01", "100"), monthly("2026-02-01", "80")),
                List.of(daily("2026-01-15", "100"), daily("2026-03-01", "10")));
        assertThat(result.passed()).isTrue();
        assertThat(result.reason()).isEqualTo(DailyMonthlyVolumeMath.REASON_MATCHED);
        assertThat(result.comparedMonths()).isEqualTo(1);
        assertThat(result.mismatches()).isEmpty();
    }

    private static ExerciseVolumeMonthlyInput monthly(String monthStart, String volume) {
        return ExerciseVolumeMonthlyInput.create(
                EXERCISE,
                LocalDate.parse(monthStart),
                new BigDecimal(volume),
                null,
                "MANUAL",
                null,
                "S001",
                NOW);
    }

    private static ExerciseVolumeDailyInput daily(String date, String volume) {
        return ExerciseVolumeDailyInput.create(
                EXERCISE,
                LocalDate.parse(date),
                new BigDecimal(volume),
                null,
                "MANUAL",
                null,
                "S001",
                NOW);
    }
}
