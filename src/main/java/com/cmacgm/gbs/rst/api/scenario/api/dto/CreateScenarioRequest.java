package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

/**
 * Create scenario payload.
 */
public record CreateScenarioRequest(
        @NotBlank String scenarioCode,
        @NotBlank String name,
        String description,
        BigDecimal rightSizingHc) {
}
