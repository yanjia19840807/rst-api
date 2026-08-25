package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Support item write payload. Server derives annual multiplier from frequency.
 * Category name is snapshotted from the database lookup; Activity is free text.
 */
public record SupportItemRequest(
        @NotNull UUID categoryId,
        @NotBlank String activity,
        @NotBlank String frequencyCode,
        @NotNull BigDecimal volume,
        @NotBlank String unitOfMeasure,
        @NotNull BigDecimal workloadPerUnitMinutes,
        String comments) {
}
