package com.cmacgm.gbs.rst.api.cycletime.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * TMS session row selected into an Exercise Embedded TMS population.
 */
public record ExerciseTmsSessionResponse(
        String sessionNo,
        String reference,
        String agentName,
        String subtaskName,
        BigDecimal processedVolume,
        long netDurationSeconds,
        Integer cycleTimeSeconds,
        Double zScore,
        boolean included,
        String exclusionReason,
        Instant startedAt,
        Instant endedAt) {
}
