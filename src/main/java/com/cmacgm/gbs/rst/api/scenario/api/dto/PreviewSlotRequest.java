package com.cmacgm.gbs.rst.api.scenario.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.cmacgm.gbs.rst.api.associateddata.api.dto.ShiftRequest;

/**
 * Slot preview request using in-memory shifts.
 */
public record PreviewSlotRequest(@NotEmpty List<@Valid @NotNull ShiftRequest> shifts) {
}
