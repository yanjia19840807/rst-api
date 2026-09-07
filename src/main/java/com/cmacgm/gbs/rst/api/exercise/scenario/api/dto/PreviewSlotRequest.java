package com.cmacgm.gbs.rst.api.exercise.scenario.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.ShiftRequest;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;

/**
 * Slot preview request using in-memory shifts.
 */
public record PreviewSlotRequest(
        @NotEmpty @Size(max = Scenario.MAX_SHIFTS) List<@Valid @NotNull ShiftRequest> shifts) {
}
