package com.cmacgm.gbs.rst.api.toolkit.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.api.dto.TimesheetAlignmentView;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSubtask;

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
        boolean enabled,
        long version,
        List<SubtaskResponse> subtasks,
        List<SharedKpiResponse> sharedKpiSelections,
        Instant deletedAt,
        int referencedEnabledSessionCount,
        int referencedDisabledSessionCount,
        int syncedSessionCount,
        boolean outOfSync,
        TimesheetAlignmentView alignment) {

    public record SessionImpact(int enabledCount, int disabledCount) {
        public static SessionImpact none() {
            return new SessionImpact(0, 0);
        }
    }

    public static ToolkitResponse from(Toolkit toolkit) {
        return from(toolkit, null, SessionImpact.none(), Map.of(), 0, true);
    }

    public static ToolkitResponse from(Toolkit toolkit, TimesheetAlignmentView alignment) {
        return from(toolkit, alignment, SessionImpact.none(), Map.of(), 0, true);
    }

    public static ToolkitResponse from(
            Toolkit toolkit,
            TimesheetAlignmentView alignment,
            SessionImpact toolkitImpact,
            Map<UUID, SessionImpact> subtaskImpacts,
            int syncedSessionCount,
            boolean includeDisabledSubtasks) {
        SessionImpact safeToolkit = toolkitImpact == null ? SessionImpact.none() : toolkitImpact;
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
                toolkit.isEnabled(),
                toolkit.getVersion(),
                toolkit.getAllSubtasks().stream()
                        .filter(subtask -> includeSubtask(subtask, includeDisabledSubtasks))
                        .map(subtask -> {
                            SessionImpact impact = subtaskImpacts.getOrDefault(
                                    subtask.getId(), SessionImpact.none());
                            return new SubtaskResponse(
                                    subtask.getId(),
                                    subtask.getName(),
                                    subtask.getDescription(),
                                    subtask.getDisplayOrder(),
                                    subtask.getDeletedAt(),
                                    subtask.isEnabled(),
                                    impact.enabledCount(),
                                    impact.disabledCount());
                        })
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
                safeToolkit.enabledCount(),
                safeToolkit.disabledCount(),
                syncedSessionCount,
                alignment != null && alignment.structuralDrift(),
                alignment);
    }

    private static boolean includeSubtask(ToolkitSubtask subtask, boolean includeDisabledSubtasks) {
        if (subtask.getDeletedAt() != null) {
            return includeDisabledSubtasks;
        }
        return includeDisabledSubtasks || subtask.isEnabled();
    }

    public record SubtaskResponse(
            UUID id,
            String name,
            String description,
            int displayOrder,
            Instant deletedAt,
            boolean enabled,
            int referencedEnabledSessionCount,
            int referencedDisabledSessionCount) {
    }

    public record SharedKpiResponse(
            UUID id, String carrier, String site, String customerCountry) {
    }
}
