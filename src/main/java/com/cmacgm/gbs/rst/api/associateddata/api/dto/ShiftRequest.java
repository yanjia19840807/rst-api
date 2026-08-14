package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

/**
 * Shift request row.
 */
public record ShiftRequest(
        short shiftNo, @NotNull LocalTime startTime, BigDecimal durationMinutes,
        @NotNull BigDecimal headcount, boolean worksOnWeekend) {
}
