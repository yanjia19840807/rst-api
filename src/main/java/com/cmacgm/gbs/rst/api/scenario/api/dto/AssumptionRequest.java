package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

/**
 * Assumption write payload.
 */
public record AssumptionRequest(
        @NotBlank String parameterCode,
        BigDecimal numericValue,
        String textValue,
        Boolean booleanValue,
        String unit) {
}
