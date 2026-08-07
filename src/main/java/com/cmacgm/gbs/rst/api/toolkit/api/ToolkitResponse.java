package com.cmacgm.gbs.rst.api.toolkit.api;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;

public record ToolkitResponse(
        UUID id,
        String name,
        String description,
        String supervisorPositionId,
        String center,
        String domain,
        String pl1,
        String pl2,
        String pl3Code,
        String pl3Name,
        boolean combineSubtasksTime,
        long version,
        List<SubtaskResponse> subtasks,
        List<SharedKpiResponse> sharedKpiSelections,
        Instant deletedAt) {

    public static ToolkitResponse from(Toolkit toolkit) {
        return new ToolkitResponse(
                toolkit.getId(),
                toolkit.getName(),
                toolkit.getDescription(),
                toolkit.getSupervisorPositionId(),
                toolkit.getCenter(),
                toolkit.getDomain(),
                toolkit.getPl1(),
                toolkit.getPl2(),
                toolkit.getPrimaryPl3Code(),
                toolkit.getPl3Name(),
                toolkit.isCombineSubtasksTime(),
                toolkit.getVersion(),
                toolkit.getAllSubtasks().stream()
                        .map(subtask -> new SubtaskResponse(
                                subtask.getId(),
                                subtask.getName(),
                                subtask.getDescription(),
                                subtask.getDisplayOrder(),
                                subtask.getDeletedAt()))
                        .toList(),
                toolkit.getSharedKpiSelections().stream()
                        .filter(selection -> selection.getDeletedAt() == null)
                        .map(selection -> new SharedKpiResponse(
                                selection.getId(),
                                selection.getCarrier(),
                                selection.getSite(),
                                selection.getCustomerCountry()))
                        .toList(),
                toolkit.getDeletedAt());
    }

    public record SubtaskResponse(
            UUID id, String name, String description, int displayOrder, Instant deletedAt) {
    }

    public record SharedKpiResponse(
            UUID id, String carrier, String site, String customerCountry) {
    }
}
