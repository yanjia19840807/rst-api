package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup.TeamSetupInput;

/**
 * Team Setup PUT payload.
 */
public record TeamSetupRequest(
        BigDecimal agentsLt6m, BigDecimal agents6To24m, BigDecimal agents24To48m,
        BigDecimal agentsGt48m, BigDecimal paidLeaveDays, BigDecimal otherLeaveDays,
        BigDecimal availabilityRatio, BigDecimal automationRatio,
        BigDecimal maxOvertimeMinutes, String slaType, BigDecimal slaTargetRatio,
        BigDecimal slaTurnaroundMinutes, LocalTime slaStartTime, LocalTime slaEndTime,
        Boolean slaWeekendEnabled, BigDecimal weekendShiftHc, BigDecimal skeletonRatio) {
    /**
     * Converts to domain input.
     *
     * @return domain input
     */
    public TeamSetupInput toInput() {
        return new TeamSetupInput(
                agentsLt6m, agents6To24m, agents24To48m, agentsGt48m,
                paidLeaveDays, otherLeaveDays,
                availabilityRatio, automationRatio, maxOvertimeMinutes,
                slaType, slaTargetRatio, slaTurnaroundMinutes, slaStartTime, slaEndTime,
                slaWeekendEnabled, weekendShiftHc, skeletonRatio);
    }
}
