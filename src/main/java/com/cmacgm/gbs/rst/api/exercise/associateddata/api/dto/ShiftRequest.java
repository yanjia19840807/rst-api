package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Shift request row. {@code weekendCode} is Excel Volume per Slot "Team Weekend"
 * ({@code NETWORKDAYS.INTL} 1–7 or 11–17).
 */
public record ShiftRequest(
        short shiftNo, @NotNull LocalTime startTime, BigDecimal durationMinutes,
        @NotNull BigDecimal headcount, @NotBlank String weekendCode) {
}
