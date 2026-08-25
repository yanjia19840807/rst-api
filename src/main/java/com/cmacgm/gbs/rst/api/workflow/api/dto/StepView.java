package com.cmacgm.gbs.rst.api.workflow.api.dto;

/**
 * Workflow step view.
 */
public record StepView(
        short stepNo,
        String requiredRoleCode,
        String assigneeCcgid,
        String assigneePositionId,
        String assigneeDisplayName,
        String routingStatus) {
}
