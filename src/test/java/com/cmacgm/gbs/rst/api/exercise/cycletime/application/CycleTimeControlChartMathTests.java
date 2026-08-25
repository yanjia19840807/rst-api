package com.cmacgm.gbs.rst.api.exercise.cycletime.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.exercise.cycletime.application.SystemCycleTimeBaselineWriter.DatedSample;
import org.junit.jupiter.api.Test;

class CycleTimeControlChartMathTests {

    @Test
    void emptySamplesYieldAnEmptyChart() {
        var chart = CycleTimeControlChartMath.build(List.of());
        assertThat(chart.points()).isEmpty();
        assertThat(chart.sampleCount()).isZero();
        assertThat(chart.upperControlLimitSeconds()).isNull();
        assertThat(chart.lowerControlLimitSeconds()).isNull();
    }

    @Test
    void bucketsByUtcDayAndComputesDailyAndRollingMedians() {
        var chart = CycleTimeControlChartMath.build(List.of(
                sample("2026-08-01T10:00:00Z", 10),
                sample("2026-08-01T18:00:00Z", 30),
                sample("2026-08-02T09:00:00Z", 50)));

        assertThat(chart.sampleCount()).isEqualTo(3);
        assertThat(chart.points()).hasSize(2);
        assertThat(chart.points().get(0).date()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(chart.points().get(0).dailyMedianSeconds()).isEqualByComparingTo("20");
        assertThat(chart.points().get(0).rollingMedianSeconds()).isEqualByComparingTo("20");
        assertThat(chart.points().get(1).date()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(chart.points().get(1).dailyMedianSeconds()).isEqualByComparingTo("50");
        assertThat(chart.points().get(1).rollingMedianSeconds()).isEqualByComparingTo("35");
        assertThat(chart.upperControlLimitSeconds()).isNotNull();
        assertThat(chart.lowerControlLimitSeconds()).isEqualByComparingTo("0");
    }

    @Test
    void omitsControlLimitsWhenOnlyOneDayExists() {
        var chart = CycleTimeControlChartMath.build(List.of(
                sample("2026-08-01T10:00:00Z", 12),
                sample("2026-08-01T12:00:00Z", 18)));

        assertThat(chart.points()).hasSize(1);
        assertThat(chart.points().getFirst().dailyMedianSeconds()).isEqualByComparingTo("15");
        assertThat(chart.upperControlLimitSeconds()).isNull();
        assertThat(chart.points().getFirst().outlier()).isFalse();
    }

    @Test
    void flagsADailyMedianOutsideTwoSigma() {
        var chart = CycleTimeControlChartMath.build(List.of(
                sample("2026-08-01T00:00:00Z", 10),
                sample("2026-08-02T00:00:00Z", 11),
                sample("2026-08-03T00:00:00Z", 12),
                sample("2026-08-04T00:00:00Z", 10),
                sample("2026-08-05T00:00:00Z", 11),
                sample("2026-08-06T00:00:00Z", 80)));

        assertThat(chart.points().getLast().date()).isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(chart.points().getLast().outlier()).isTrue();
        assertThat(chart.points().getFirst().outlier()).isFalse();
    }

    private static DatedSample sample(String at, double seconds) {
        return new DatedSample(Instant.parse(at), seconds);
    }
}
