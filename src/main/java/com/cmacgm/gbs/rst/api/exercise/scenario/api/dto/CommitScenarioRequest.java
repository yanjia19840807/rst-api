package com.cmacgm.gbs.rst.api.exercise.scenario.api.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.ShiftRequest;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;

/**
 * Full scenario save payload.
 */
public record CommitScenarioRequest(
        @NotBlank String name,
        String description,
        BigDecimal rightSizingHc,
        @Size(max = Scenario.MAX_SHIFTS) List<@Valid ShiftRequest> shifts,
        CommitResults results) {
}
