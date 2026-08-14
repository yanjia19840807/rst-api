package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Team Setup response.
 */
public record TeamSetupView(
        BigDecimal agentsLt6m, BigDecimal agents6To24m, BigDecimal agents24To48m,
        BigDecimal agentsGt48m, BigDecimal deliveryHc, BigDecimal workingHoursPerDay,
        BigDecimal paidLeaveDays, BigDecimal otherLeaveDays, String weekendCode,
        BigDecimal availabilityRatio, BigDecimal automationRatio, BigDecimal capacityRatio,
        BigDecimal maxOvertimeMinutes, String slaType, BigDecimal slaTargetRatio,
        BigDecimal slaTurnaroundMinutes, LocalTime slaStartTime, LocalTime slaEndTime,
        Boolean slaWeekendEnabled, BigDecimal weekendShiftHc, BigDecimal skeletonRatio,
        BigDecimal totalAgents, BigDecimal averageTenureYears, BigDecimal workingDaysPerYear,
        BigDecimal maxCapacityDays, BigDecimal dailyCapacityPerAgent,
        String calculationVersion, long version) {
}
