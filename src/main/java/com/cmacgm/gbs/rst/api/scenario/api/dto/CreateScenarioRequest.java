package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/**
 * Create scenario payload.
 */
public record CreateScenarioRequest(
        @NotBlank String scenarioCode,
        @NotBlank String name,
        String description,
        List<AssumptionRequest> assumptions) {
}
