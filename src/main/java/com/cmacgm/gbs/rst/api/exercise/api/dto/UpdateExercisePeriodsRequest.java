package com.cmacgm.gbs.rst.api.exercise.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Update Exercise Sizing Month payload (Toolkit is immutable after create).
 */
public record UpdateExercisePeriodsRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$") String sizingMonth) {
}
