package com.cmacgm.gbs.rst.api.exercise.scenario.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Update scenario payload.
 */
public record UpdateScenarioRequest(
        @NotBlank @Size(max = 30) String name,
        String description,
        BigDecimal rightSizingHc) {
}
