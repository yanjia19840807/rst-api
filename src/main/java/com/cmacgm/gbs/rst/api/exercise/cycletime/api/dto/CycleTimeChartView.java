package com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * SYSTEM Cycle Time control-chart series derived from included Embedded TMS samples.
 */
public record CycleTimeChartView(
        List<CycleTimeChartPoint> points,
        BigDecimal upperControlLimitSeconds,
        BigDecimal lowerControlLimitSeconds,
        int sampleCount) {

    public static CycleTimeChartView empty() {
        return new CycleTimeChartView(List.of(), null, null, 0);
    }
}
