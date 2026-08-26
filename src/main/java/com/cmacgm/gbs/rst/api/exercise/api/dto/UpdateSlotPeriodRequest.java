package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Slot Period payload used to generate the Per-slot Volume grid.
 */
public record UpdateSlotPeriodRequest(
        @NotNull LocalDate slotStartDate,
        @Min(1) @Max(12) short slotWeeks) {
}
