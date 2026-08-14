package com.cmacgm.gbs.rst.api.toolkit.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateToolkitRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        boolean combineSubtasksTime,
        List<@Valid EditableSubtask> subtasks,
        List<@Valid SharedKpiSelectionRequest> sharedKpiSelections,
        @NotNull Long version) {

    public record EditableSubtask(
            UUID id,
            @NotBlank @Size(max = 200) String name,
            String description,
            @Min(0) int displayOrder,
            Instant deletedAt) {
    }
}
