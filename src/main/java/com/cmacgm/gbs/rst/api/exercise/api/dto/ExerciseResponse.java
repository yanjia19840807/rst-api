package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Exercise API response including action flags.
 */
public record ExerciseResponse(
        UUID id, String exerciseCode, UUID toolkitId, String sizingMonth,
        LocalDate slotStartDate, short slotWeeks, LocalDate tmsFrom, LocalDate tmsTo,
        String workflowStatus, String submissionStatus, UUID officialScenarioId, Instant submittedAt,
        boolean canDelete, boolean canSubmit, boolean canEdit,
        long version, Instant createdAt,
        Short currentStep, String requiredRole, String currentReviewer, String lastDecisionComment,
        BigDecimal deliveryHc, BigDecimal rightSizingHc, BigDecimal productionSupport,
        BigDecimal capacityCreation, Integer agingDays, Instant archivedAt,
        ExerciseSnapshot snapshot) {
}
