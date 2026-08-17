package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Same-PL3 benchmarking: cards from all filtered rows, one page of items, unfiltered options.
 */
public record BenchmarkingView(
        String selectedPl3,
        BigDecimal bestDailyCapacity,
        String bestDailyCapacityHint,
        BigDecimal medianCycleTimeSeconds,
        BigDecimal productionSupportRatioPct,
        List<BenchmarkRow> items,
        int page,
        int pageSize,
        long total,
        int totalPages,
        List<String> centers,
        List<String> domains,
        List<String> pl1Names,
        List<String> pl2Names,
        List<BenchmarkPl3Option> pl3Options) {
}
