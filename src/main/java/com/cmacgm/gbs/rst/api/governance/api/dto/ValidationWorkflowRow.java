package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One Validation Workflow row: an UNDER_REVIEW Exercise waiting on Manager / CDH / LTH.
 * Shared KPI scope is copied from the Exercise (not split by KPI line).
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
        List<String> carriers,
        List<String> sites,
        List<String> customerCountries,
        String currentStep,
        String currentOwner,
        String currentOwnerCcgid,
        Integer agingDays,
        BigDecimal capacityCreation,
        BigDecimal capacityPct,
        String volumeYoY,
        String sizingMonth,
        String submittedDate) {
}
