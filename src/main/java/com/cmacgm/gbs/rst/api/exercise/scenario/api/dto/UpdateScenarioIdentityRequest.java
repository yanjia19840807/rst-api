package com.cmacgm.gbs.rst.api.exercise.scenario.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Updates scenario name and description without touching simulation inputs or results.
 */
public record UpdateScenarioIdentityRequest(
        @NotBlank @Size(max = 30) String name, String description) {
}
