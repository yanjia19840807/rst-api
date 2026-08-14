package com.cmacgm.gbs.rst.api.toolkit.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateToolkitRequest(
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
        List<@Valid ToolkitSubtaskWriteRequest> subtasks,
        List<@Valid SharedKpiSelectionRequest> sharedKpiSelections) {
}
