package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Update Exercise period payload (Toolkit is immutable after create).
 */
public record UpdateExercisePeriodsRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$") String sizingMonth,
        @NotNull LocalDate tmsFrom,
        @NotNull LocalDate tmsTo) {
}
