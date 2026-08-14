package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Slot simulation response.
 */
public record SlotSimulationView(
        UUID id,
        int runNo,
        String status,
        String calculationVersion,
        UUID forecastRunId,
        Instant startedAt,
        Instant completedAt,
        BigDecimal tatOnPeriod,
        BigDecimal actualVsTheoretical,
        int shiftCount,
        boolean applicability,
        BigDecimal slaTargetRatio,
        List<SlotRowView> rows,
        SlotChartView chart) {
}
