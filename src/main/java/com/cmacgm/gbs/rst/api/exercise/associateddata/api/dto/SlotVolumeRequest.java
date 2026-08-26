package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/**
 * Slot volume request row.
 */
public record SlotVolumeRequest(
        @NotNull Instant slotStartAt,
        @NotNull Instant slotEndAt,
        BigDecimal actualVolume) {
}
