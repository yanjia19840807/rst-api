package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;

/**
 * One benchmarking row: a frozen Shared KPI line on an APPROVED Exercise.
 */
public record BenchmarkRow(
        String gbs,
        String sharedKpiLine,
        String domain,
        String pl1,
        String pl2,
        String pl3,
        String pl3Code,
        BigDecimal cycleTimeSeconds,
        BigDecimal dailyCapacityPerAgent,
        BigDecimal productionSupportRatioPct,
        BigDecimal capacityCreation,
        BigDecimal deliveryHc,
        BigDecimal productionSupport,
        String submittedDate) {
}
