package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Calendar response.
 */
public record CalendarView(
        String weekendCode, String baselineSource,
        String baselineVersion, UUID sourceTemplateId, Integer sourceTemplateVersion,
        Short baselineYear, BigDecimal workingDaysPerYear, long version,
        List<HolidayView> holidays,
        boolean templateUpdateAvailable,
        Integer publishedTemplateVersion,
        String templateUpdateMessage) {
}
