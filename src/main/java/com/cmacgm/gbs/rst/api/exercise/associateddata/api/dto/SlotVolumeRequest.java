package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * Slot volume request row (Center wall clock, no offset).
 */
public record SlotVolumeRequest(
        @NotNull LocalDateTime slotStartAt,
        @NotNull LocalDateTime slotEndAt,
        BigDecimal actualVolume) {
}
