package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Same-PL3 benchmarking: cards from all filtered rows, one page of items, cascade paths.
 */
public record BenchmarkingView(
        String selectedPl3,
        BigDecimal dailyCapacityPerAgent,
        BigDecimal cycleTimeSeconds,
        BigDecimal productionSupportRatioPct,
        List<BenchmarkRow> items,
        int page,
        int pageSize,
        long total,
        int totalPages,
        List<BenchmarkProcessPath> processPaths) {
}
