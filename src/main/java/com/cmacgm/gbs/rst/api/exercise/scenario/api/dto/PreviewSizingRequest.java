package com.cmacgm.gbs.rst.api.exercise.scenario.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/**
 * Preview request with HC from the form (not yet saved).
 */
public record PreviewSizingRequest(@NotNull BigDecimal rightSizingHc) {
}
