package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.time.LocalDate;

/**
 * Supervisor Exercise list query (tab + field filters).
 */
public record ExerciseListQuery(
        String tab,
        String exerciseCode,
        String toolkitName,
        String pl3Name,
        String workflowStatus,
        String reviewStage,
        String handler,
        String officialScenario,
        LocalDate createdFrom,
        LocalDate createdTo,
        LocalDate submittedFrom,
        LocalDate submittedTo,
        LocalDate archivedFrom,
        LocalDate archivedTo) {
}
