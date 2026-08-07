package com.cmacgm.gbs.rst.api.toolkit.api;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ToolkitRequests {

    private ToolkitRequests() {
    }

    public record Create(
            @NotBlank @Size(max = 200) String name,
            String description,
            @NotBlank String supervisorPositionId,
            @NotBlank String center,
            @NotBlank String domain,
            @NotBlank String pl1,
            @NotBlank String pl2,
            @NotBlank String pl3Code,
            @NotBlank String pl3Name,
            boolean combineSubtasksTime,
            List<@Valid Subtask> subtasks,
            List<@Valid SharedKpi> sharedKpiSelections) {
    }

    public record Update(
            @NotBlank @Size(max = 200) String name,
            String description,
            boolean combineSubtasksTime,
            List<@Valid EditableSubtask> subtasks,
            List<@Valid SharedKpi> sharedKpiSelections,
            @NotNull Long version) {
    }

    public record EditableSubtask(
            UUID id,
            @NotBlank @Size(max = 200) String name,
            String description,
            @Min(0) int displayOrder,
            Instant deletedAt) {
    }

    public record Subtask(
            @NotBlank @Size(max = 200) String name,
            String description,
            @Min(0) int displayOrder) {
    }

    public record SharedKpi(
            @NotBlank String carrier,
            @NotBlank String site,
            @NotBlank String customerCountry) {
    }
}
