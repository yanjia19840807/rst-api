package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * TMS Period payload used to link COMPLETED sessions for the SYSTEM median.
 */
public record UpdateTmsPeriodRequest(
        @NotNull LocalDate tmsFrom,
        @NotNull LocalDate tmsTo) {
}
