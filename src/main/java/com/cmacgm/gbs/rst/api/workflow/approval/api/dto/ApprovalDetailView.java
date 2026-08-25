package com.cmacgm.gbs.rst.api.workflow.approval.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.workflow.api.dto.ActionView;
import com.cmacgm.gbs.rst.api.workflow.api.dto.ScopeView;
import com.cmacgm.gbs.rst.api.workflow.api.dto.StepView;

/**
 * Approver review detail (extends Submitted Details fields).
 */
public record ApprovalDetailView(
        UUID exerciseId,
        String exerciseCode,
        String workflowStatus,
        Instant submittedAt,
        UUID scenarioId,
        String scenarioName,
        UUID submissionId,
        String submissionStatus,
        Short currentStep,
        String requiredRole,
        String remarks,
        List<ScopeView> scopes,
        List<StepView> steps,
        List<ActionView> actions,
        boolean canDecide,
        ApprovalWorkspaceView workspace) {
}
