package com.cmacgm.gbs.rst.api.exercise.associateddata.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TmsRatioMathTests {

    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");
    private static final UUID EXERCISE = UUID.fromString("d03a4c9a-50f2-4caf-b4e8-e1886af53eba");
    private static final LocalDate FROM = LocalDate.parse("2026-08-01");
    private static final LocalDate TO = LocalDate.parse("2026-08-03");

    @Test
    void incompleteCoverageWhenADateHasNullActual() {
        TmsRatioMath.Result result = TmsRatioMath.compute(
                FROM,
                TO,
                List.of(daily("2026-08-01", "10"), daily("2026-08-02", null), daily("2026-08-03", "10")),
                new BigDecimal("15"));
        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).isEqualTo(TmsRatioMath.REASON_INCOMPLETE_COVERAGE);
        assertThat(result.ratio()).isNull();
        assertThat(result.missingDateCount()).isEqualTo(1);
        assertThat(result.missingDates()).containsExactly("2026-08-02");
    }

    @Test
    void incompleteCoverageWhenADateIsMissing() {
        TmsRatioMath.Result result = TmsRatioMath.compute(
                FROM,
                TO,
                List.of(daily("2026-08-01", "10"), daily("2026-08-03", "10")),
                new BigDecimal("15"));
        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).isEqualTo(TmsRatioMath.REASON_INCOMPLETE_COVERAGE);
        assertThat(result.missingDateCount()).isEqualTo(1);
    }

    @Test
    void zeroIsValidDailyData() {
        TmsRatioMath.Result result = TmsRatioMath.compute(
                FROM,
                TO,
                List.of(daily("2026-08-01", "10"), daily("2026-08-02", "0"), daily("2026-08-03", "10")),
                new BigDecimal("16"));
        assertThat(result.passed()).isTrue();
        assertThat(result.reason()).isEqualTo(TmsRatioMath.REASON_OK);
        assertThat(result.ratio()).isEqualByComparingTo("0.800000");
    }

    @Test
    void belowThresholdIsWarning() {
        TmsRatioMath.Result result = TmsRatioMath.compute(
                FROM,
                TO,
                List.of(daily("2026-08-01", "10"), daily("2026-08-02", "10"), daily("2026-08-03", "10")),
                new BigDecimal("15"));
        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).isEqualTo(TmsRatioMath.REASON_BELOW_THRESHOLD);
        assertThat(result.ratio()).isEqualByComparingTo("0.500000");
        assertThat(result.tmsVolumeSum()).isEqualByComparingTo("15");
        assertThat(result.dailyVolumeSum()).isEqualByComparingTo("30");
    }

    @Test
    void dailyVolumeZeroCannotComputeRatio() {
        TmsRatioMath.Result result = TmsRatioMath.compute(
                FROM,
                TO,
                List.of(daily("2026-08-01", "0"), daily("2026-08-02", "0"), daily("2026-08-03", "0")),
                new BigDecimal("12"));
        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).isEqualTo(TmsRatioMath.REASON_DAILY_VOLUME_ZERO);
        assertThat(result.ratio()).isNull();
    }

    @Test
    void datesOutsidePeriodAreIgnored() {
        TmsRatioMath.Result result = TmsRatioMath.compute(
                FROM,
                TO,
                List.of(
                        daily("2026-07-31", "999"),
                        daily("2026-08-01", "10"),
                        daily("2026-08-02", "10"),
                        daily("2026-08-03", "10"),
                        daily("2026-08-04", "999")),
                new BigDecimal("30"));
        assertThat(result.passed()).isTrue();
        assertThat(result.dailyVolumeSum()).isEqualByComparingTo("30");
        assertThat(result.ratio()).isEqualByComparingTo("1.000000");
    }

    private static ExerciseVolumeDailyInput daily(String date, String volume) {
        return ExerciseVolumeDailyInput.create(
                EXERCISE,
                LocalDate.parse(date),
                volume == null ? null : new BigDecimal(volume),
                null,
                "MANUAL",
                null,
                "S001",
                NOW);
    }
}
