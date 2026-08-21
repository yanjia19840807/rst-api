package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.util.List;

/**
 * Calendar PUT payload: Supervisor-owned holiday list.
 */
public record CalendarRequest(List<HolidayRequest> holidays) {
}
