package com.cmacgm.gbs.rst.api.cycletime.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Manual baseline create payload.
 */
public record ManualBaselineRequest(
        @NotNull @Positive BigDecimal medianSeconds,
        @NotBlank String manualReason,
        List<UUID> fileArtifactIds) {
}
