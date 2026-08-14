package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Chart series for slot result visualization.
 */
public record SlotChartView(
        List<String> labels,
        List<BigDecimal> theoreticalFte,
        Map<String, List<BigDecimal>> shiftFteByKey,
        List<BigDecimal> cumulativeTat) {
}
