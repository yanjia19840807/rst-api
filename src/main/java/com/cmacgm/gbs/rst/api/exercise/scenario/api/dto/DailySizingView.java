package com.cmacgm.gbs.rst.api.exercise.scenario.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Latest daily simulation response.
 */
public record DailySizingView(
        UUID id,
        int runNo,
        String status,
        String calculationVersion,
        UUID forecastRunId,
        Instant startedAt,
        Instant completedAt,
        List<DailySizingRowView> rows) {
}
