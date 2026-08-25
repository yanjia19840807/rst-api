package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.util.List;

/**
 * Calendar response: Supervisor-owned holiday rows.
 */
public record CalendarView(List<HolidayView> holidays) {
}
