package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.util.List;

/**
 * Calendar PUT payload.
 */
public record CalendarRequest(
        String weekendCode, String baselineSource,
        String baselineVersion, List<HolidayRequest> holidays) {
}
