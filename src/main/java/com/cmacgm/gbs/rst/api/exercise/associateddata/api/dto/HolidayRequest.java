package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Holiday request row.
 */
public record HolidayRequest(
        @NotNull LocalDate holidayDate, String holidayName,
        @NotBlank String holidayType) {
}
