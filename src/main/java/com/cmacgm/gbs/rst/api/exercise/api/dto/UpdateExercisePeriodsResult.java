package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.util.List;

/**
 * Update periods response with initialization notices.
 */
public record UpdateExercisePeriodsResult(ExerciseResponse exercise, List<String> notices) {
}
