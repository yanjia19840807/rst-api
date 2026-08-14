package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import com.cmacgm.gbs.rst.api.associateddata.api.dto.ShiftRequest;

/**
 * Full scenario save payload.
 */
public record CommitScenarioRequest(
        @NotBlank String name,
        String description,
        List<AssumptionRequest> assumptions,
        List<@Valid ShiftRequest> shifts,
        CommitResults results) {
}
