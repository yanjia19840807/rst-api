package com.cmacgm.gbs.rst.api.scenario.application.sizing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class SizingMathTests {

    @Test
    void actualHeadcountPrefersTeamSetupTotalAgent() {
        assertThat(SizingMath.actualHeadcount(new BigDecimal("12.6"), new BigDecimal("11.12")))
                .isEqualByComparingTo("12.6");
        assertThat(SizingMath.actualHeadcount(null, new BigDecimal("11.12")))
                .isEqualByComparingTo("11.12");
    }

    @Test
    void measuredRightSizingHcTreatsMissingOrZeroAsNoResult() {
        assertThat(SizingMath.measuredRightSizingHc(null)).isNull();
        assertThat(SizingMath.measuredRightSizingHc(BigDecimal.ZERO)).isNull();
        assertThat(SizingMath.measuredRightSizingHc(new BigDecimal("-1"))).isNull();
        assertThat(SizingMath.measuredRightSizingHc(new BigDecimal("12.50")))
                .isEqualByComparingTo("12.50");
    }

    @Test
    void capacityCreationUsesActualHeadcount() {
        assertThat(SizingMath.capacityCreation(
                new BigDecimal("12.6"), new BigDecimal("12.50"), new BigDecimal("2.055343")))
                .isEqualByComparingTo("-1.955343");
    }

    @Test
    void dailyHistoryWindowIsSizingMonthMinusTwoThroughSizingMonth() {
        YearMonth sizing = YearMonth.of(2026, 6);
        assertThat(SizingMath.dailyHistoryStart(sizing)).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(SizingMath.dailyHistoryEnd(sizing)).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(SizingMath.monthlyHistoryMonths(sizing)).containsExactly(
                YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6));
    }

    @Test
    void dailyFullPeriodRunsFromFirstActualThroughForecastMonth() {
        YearMonth sizing = YearMonth.of(2026, 6);
        assertThat(SizingMath.dailyFullPeriodStart(sizing, LocalDate.of(2026, 1, 1)))
                .isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(SizingMath.dailyFullPeriodStart(sizing, null))
                .isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(SizingMath.dailyFullPeriodEnd(sizing)).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void monthlyManualVolumeAppliesAutomation() {
        BigDecimal manual = SizingMath.monthlyManualVolume(
                new BigDecimal("1000"),
                new BigDecimal("0.10"),
                BigDecimal.ZERO);
        assertThat(manual).isEqualByComparingTo("900.000000");
    }

    @Test
    void monthlyManualVolumeAppliesCommercialRatio() {
        BigDecimal manual = SizingMath.monthlyManualVolume(
                new BigDecimal("1000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"));
        assertThat(manual).isEqualByComparingTo("945.000000");
    }

    @Test
    void dailyManualVolumeAppliesCommercialAndDailyAdj() {
        BigDecimal manual = SizingMath.dailyManualVolume(
                new BigDecimal("100"),
                new BigDecimal("0.20"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"));
        assertThat(manual).isEqualByComparingTo("92.400000");
    }

    @Test
    void nominalHcWithoutOtRoundsUp() {
        BigDecimal hc = SizingMath.nominalHcWithoutOt(
                new BigDecimal("945"),
                new BigDecimal("120"),
                new BigDecimal("22"),
                new BigDecimal("8"),
                new BigDecimal("0.85"),
                new BigDecimal("0.90"));
        assertThat(hc).isEqualByComparingTo("1");
    }

    @Test
    void backlogEndDoesNotGoNegative() {
        BigDecimal end = SizingMath.backlogEnd(
                BigDecimal.ZERO,
                new BigDecimal("50"),
                new BigDecimal("80"),
                new BigDecimal("10"));
        assertThat(end).isEqualByComparingTo("0.000000");
    }

    @Test
    void overtimeCapacityAcceptsFractionalMinutes() {
        BigDecimal overtime = SizingMath.overtimeCapacity(
                true,
                new BigDecimal("2"),
                new BigDecimal("0.5"),
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("60"));
        assertThat(overtime).isEqualByComparingTo("1");
    }

    @Test
    void standardCapacityRoundsDownLikeExcel() {
        BigDecimal capacity = SizingMath.standardCapacity(
                new BigDecimal("2"),
                new BigDecimal("8"),
                new BigDecimal("0.85"),
                new BigDecimal("0.90"),
                new BigDecimal("120"));
        assertThat(capacity).isEqualByComparingTo("367");
    }
}
