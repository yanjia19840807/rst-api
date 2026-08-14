package com.cmacgm.gbs.rst.api.exercise.api.dto;

import java.util.List;

/**
 * Update periods response with optional holiday re-apply notices.
 */
public record UpdateExercisePeriodsResult(ExerciseResponse exercise, List<String> notices) {
}
