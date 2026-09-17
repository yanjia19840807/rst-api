package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;

/**
 * One GBS Center's comparable metrics for the selected PL3.
 * Cycle time, daily capacity, and Support ratio are Center-weighted;
 * Capacity Creation is the sum of filtered Shared KPI lines.
 */
public record BenchmarkCenterComparison(
        String gbs,
        BigDecimal cycleTimeSeconds,
        BigDecimal dailyCapacityPerAgent,
        BigDecimal productionSupportRatioPct,
        BigDecimal capacityCreation) {
}
