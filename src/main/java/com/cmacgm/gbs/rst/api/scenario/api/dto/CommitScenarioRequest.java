package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.math.BigDecimal;
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
        BigDecimal rightSizingHc,
        List<@Valid ShiftRequest> shifts,
        CommitResults results) {
}
