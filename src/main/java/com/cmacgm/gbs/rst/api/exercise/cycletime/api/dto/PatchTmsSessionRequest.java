package com.cmacgm.gbs.rst.api.exercise.cycletime.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * PATCH inclusion payload.
 */
public record PatchTmsSessionRequest(@NotNull Boolean included) {
}
