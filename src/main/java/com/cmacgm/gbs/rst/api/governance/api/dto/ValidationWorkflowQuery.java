package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.time.LocalDate;

/**
 * Validation Workflow list filters (field values applied on the server).
 */
public record ValidationWorkflowQuery(
        String exerciseCode,
        String center,
        String domain,
        String pl3Name,
        String toolkitName,
        Integer agingMinDays,
        LocalDate submittedFrom,
        LocalDate submittedTo) {
}
