package com.cmacgm.gbs.rst.api.cycletime.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * PATCH inclusion payload.
 */
public record PatchTmsSessionRequest(@NotNull Boolean included) {
}
