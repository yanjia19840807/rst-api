package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Shift response.
 */
public record ShiftView(
        UUID id, short shiftNo, LocalTime startTime, BigDecimal durationMinutes,
        BigDecimal headcount, boolean worksOnWeekend) {
}
