package com.cmacgm.gbs.rst.api.toolkit.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.api.dto.TimesheetAlignmentView;
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
        Instant deletedAt,
        boolean outOfSync,
        TimesheetAlignmentView alignment) {

    public static ToolkitResponse from(Toolkit toolkit) {
        return from(toolkit, null);
    }

    /**
     * Maps a Toolkit and optional live Timesheet alignment.
     *
     * @param toolkit aggregate
     * @param alignment structural alignment, or null
     * @return response
     */
    public static ToolkitResponse from(Toolkit toolkit, TimesheetAlignmentView alignment) {
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
                toolkit.getDeletedAt(),
                alignment != null && alignment.structuralDrift(),
                alignment);
    }

    public record SubtaskResponse(
            UUID id, String name, String description, int displayOrder, Instant deletedAt) {
    }

    public record SharedKpiResponse(
            UUID id, String carrier, String site, String customerCountry) {
    }
}
