package com.cmacgm.gbs.rst.api.associateddata.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Support item write payload. {@code annualMultiplier} is ignored; server derives it.
 */
public record SupportItemRequest(
        @NotBlank String category,
        @NotBlank String activity,
        @NotBlank String frequencyCode,
        @NotNull BigDecimal volume,
        @NotBlank String unitOfMeasure,
        @NotNull BigDecimal workloadPerUnitMinutes,
        BigDecimal annualMultiplier,
        String comments) {
}
