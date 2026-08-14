package com.cmacgm.gbs.rst.api.submission.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalWorkspaceView;

/**
 * Submitted details response.
 */
public record SubmittedDetailsView(
        UUID exerciseId,
        String exerciseCode,
        String workflowStatus,
        Instant submittedAt,
        UUID scenarioId,
        String scenarioName,
        UUID submissionId,
        String submissionCode,
        String submissionStatus,
        Short currentStep,
        String requiredRole,
        String remarks,
        List<ScopeView> scopes,
        UUID workflowInstanceId,
        String workflowStatusLabel,
        List<StepView> steps,
        List<ActionView> actions,
        ApprovalWorkspaceView workspace) {
}
