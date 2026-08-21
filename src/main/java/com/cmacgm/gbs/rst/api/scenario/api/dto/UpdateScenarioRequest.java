package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

/**
 * Update scenario payload.
 */
public record UpdateScenarioRequest(
        @NotBlank String name,
        String description,
        BigDecimal rightSizingHc) {
}
