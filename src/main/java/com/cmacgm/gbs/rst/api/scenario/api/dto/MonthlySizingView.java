package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Latest monthly sizing response.
 */
public record MonthlySizingView(
        UUID id,
        int runNo,
        String status,
        String calculationVersion,
        UUID forecastRunId,
        Instant startedAt,
        Instant completedAt,
        List<MonthlySizingRowView> rows) {
}
