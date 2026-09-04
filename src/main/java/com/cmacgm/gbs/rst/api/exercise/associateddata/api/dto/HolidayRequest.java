package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import com.cmacgm.gbs.rst.api.common.workingdays.HolidayDayKind;

/**
 * Holiday request row.
 */
public record HolidayRequest(
        @NotNull LocalDate holidayDate, String holidayName,
        @NotNull HolidayDayKind holidayType) {
}
