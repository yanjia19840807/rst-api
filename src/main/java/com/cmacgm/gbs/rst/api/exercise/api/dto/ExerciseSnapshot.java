package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Exercise snapshot envelope.
 */
public record ExerciseSnapshot(
        ExerciseToolkitView toolkit, List<ExerciseSubtaskView> subtasks,
        List<ExerciseKpiView> sharedKpis, LocalDate timesheetSyncDate) {
}
