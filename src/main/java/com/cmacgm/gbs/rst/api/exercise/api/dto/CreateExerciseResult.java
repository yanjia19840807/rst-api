package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.util.List;

/**
 * Create Exercise response with initialization notices for the Supervisor.
 */
public record CreateExerciseResult(ExerciseResponse exercise, List<String> notices) {
}
