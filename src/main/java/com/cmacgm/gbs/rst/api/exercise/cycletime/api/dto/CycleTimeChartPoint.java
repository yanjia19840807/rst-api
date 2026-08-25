package com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One UTC day on the Cycle Time control chart.
 */
public record CycleTimeChartPoint(
        LocalDate date,
        BigDecimal dailyMedianSeconds,
        BigDecimal rollingMedianSeconds,
        boolean outlier) {
}
