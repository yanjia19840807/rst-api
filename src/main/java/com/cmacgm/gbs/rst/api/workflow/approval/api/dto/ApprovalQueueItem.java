package com.cmacgm.gbs.rst.api.workflow.approval.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Approver queue row. {@code completedTaskId} is the finished review visit
 * on Completed Task; Awaiting Review leaves it null.
 */
public record ApprovalQueueItem(
        UUID submissionId,
        UUID completedTaskId,
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
        String completedStep,
        boolean scopeChanged) {
}
