package com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Active baseline response.
 */
public record BaselineView(
        UUID id,
        String baselineType,
        BigDecimal medianSeconds,
        Integer sampleCount,
        String calculationMethod,
        String manualReason,
        boolean active,
        Instant calculatedAt,
        List<BaselineFileView> files) {
}
