package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Slot Simulation shift input on a Scenario.
 */
public record ShiftView(
        UUID id,
        short shiftNo,
        LocalTime startTime,
        BigDecimal durationMinutes,
        BigDecimal headcount,
        boolean worksOnWeekend) {
}
