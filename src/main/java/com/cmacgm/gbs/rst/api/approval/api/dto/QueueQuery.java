package com.cmacgm.gbs.rst.api.approval.api.dto;

import java.time.LocalDate;

/**
 * Approver queue query (tab + field filters).
 */
public record QueueQuery(
        String status,
        boolean completed,
        String exerciseCode,
        String toolkitName,
        String pl3Name,
        LocalDate submittedFrom,
        LocalDate submittedTo,
        LocalDate completedFrom,
        LocalDate completedTo,
        String decision) {
}
