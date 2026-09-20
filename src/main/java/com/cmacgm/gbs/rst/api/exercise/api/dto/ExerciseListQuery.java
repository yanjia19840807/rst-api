package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.time.LocalDate;

/**
 * Supervisor Exercise list query (tab + field filters).
 */
public record ExerciseListQuery(
        String tab,
        String exerciseCode,
        String toolkitName,
        String center,
        String domain,
        String pl3Name,
        String carrier,
        String site,
        String customerCountry,
        String workflowStatus,
        String reviewStage,
        String handler,
        String officialScenario,
        String sizingMonth,
        LocalDate createdFrom,
        LocalDate createdTo,
        LocalDate submittedFrom,
        LocalDate submittedTo,
        LocalDate archivedFrom,
        LocalDate archivedTo) {
}
