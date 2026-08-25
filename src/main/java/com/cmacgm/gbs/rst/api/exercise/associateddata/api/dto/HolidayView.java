package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Holiday response.
 */
public record HolidayView(
        UUID id, LocalDate holidayDate, String holidayName, String holidayType) {
}
