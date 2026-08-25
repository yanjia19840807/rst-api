package com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto;

/**
 * PATCH result with refreshed session and active baseline.
 */
public record PatchTmsSessionResult(
        ExerciseTmsSessionResponse session,
        BaselineView baseline) {
}
