package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Create Exercise request payload.
 */
public record CreateExerciseRequest(
        @NotNull UUID toolkitId,
        @NotBlank @Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$") String sizingMonth,
        @NotNull LocalDate tmsFrom,
        @NotNull LocalDate tmsTo) {
}
