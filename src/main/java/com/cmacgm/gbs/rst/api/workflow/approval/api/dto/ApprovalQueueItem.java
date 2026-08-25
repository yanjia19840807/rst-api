package com.cmacgm.gbs.rst.api.workflow.approval.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Approver queue row.
 */
public record ApprovalQueueItem(
        UUID submissionId,
        UUID exerciseId,
        String exerciseCode,
        String center,
        String domain,
        String pl3Name,
        String toolkitName,
        String supervisor,
        BigDecimal deliveryHc,
        BigDecimal rightSizingHc,
        BigDecimal productionSupport,
        BigDecimal capacityCreation,
        String previousStep,
        String previousActor,
        Instant previousStepAt,
        Integer agingDays,
        Instant createdAt,
        Instant submittedAt,
        Instant archivedAt,
        String finalStatus,
        Integer reviewDurationDays,
        String status,
        String myDecision,
        Instant myCompletedAt,
        String completedStep) {
}
