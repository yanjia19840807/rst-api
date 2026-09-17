package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;

/**
 * One Validation Workflow row: an UNDER_REVIEW Exercise waiting on Manager / CDH / LTH.
 */
public record ValidationWorkflowRow(
        String exerciseNo,
        String exerciseUuid,
        String gbs,
        String domain,
        String pl1,
        String pl2,
        String pl3,
        String toolkit,
        String currentStep,
        String currentOwner,
        Integer agingDays,
        BigDecimal capacityCreation,
        BigDecimal capacityPct,
        String volumeYoY,
        String sizingMonth,
        String submittedDate) {
}
