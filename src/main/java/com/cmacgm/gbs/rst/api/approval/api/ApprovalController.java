package com.cmacgm.gbs.rst.api.approval.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import com.cmacgm.gbs.rst.api.approval.application.ApprovalService;
import com.cmacgm.gbs.rst.api.approval.application.ApprovalService.ApprovalDetailView;
import com.cmacgm.gbs.rst.api.approval.application.ApprovalService.ApprovalQueueItem;
import com.cmacgm.gbs.rst.api.approval.application.ApprovalService.ApproveRequest;
import com.cmacgm.gbs.rst.api.approval.application.ApprovalService.ReturnRequest;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Approver Approval Queue and Submission Review (Approve / Return).
 */
@RestController
@RequestMapping("/api/v1/approvals")
@PreAuthorize("hasAnyRole('MANAGER','CDH','LTH','SUPERVISOR')")
public class ApprovalController {

    private final ApprovalService approvals;

    /**
     * Creates the Approval controller.
     *
     * @param approvals approval service
     */
    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    /**
     * Lists submissions awaiting review, or archived when {@code archived=true}.
     *
     * @param status optional status filter; default {@code AWAITING} for open queue
     * @param archived when true, list VALIDATED/RETURNED/ARCHIVED
     * @return queue items
     */
    @GetMapping("/queue")
    public List<ApprovalQueueItem> queue(
            @RequestParam(required = false, defaultValue = "AWAITING") String status,
            @RequestParam(required = false, defaultValue = "false") boolean archived) {
        return approvals.queue(status, archived);
    }

    /**
     * Returns review detail for a submission.
     *
     * @param submissionId submission id
     * @return review detail
     */
    @GetMapping("/{submissionId}")
    public ApprovalDetailView detail(@PathVariable UUID submissionId) {
        return approvals.detail(submissionId);
    }

    /**
     * Approves the current READY workflow step.
     *
     * @param principal acting principal
     * @param submissionId submission id
     * @param request optional comments / requestId
     * @return updated review detail
     */
    @PostMapping("/{submissionId}/approve")
    public ApprovalDetailView approve(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID submissionId,
            @RequestBody(required = false) ApproveBody request) {
        ApproveBody payload = request == null ? new ApproveBody(null, null) : request;
        return approvals.approve(
                principal.userId(),
                submissionId,
                new ApproveRequest(payload.comments(), payload.requestId()));
    }

    /**
     * Returns the submission to the Supervisor.
     *
     * @param principal acting principal
     * @param submissionId submission id
     * @param request return payload with required comments
     * @return updated review detail
     */
    @PostMapping("/{submissionId}/return")
    public ApprovalDetailView returnToSupervisor(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID submissionId,
            @Valid @RequestBody ReturnBody request) {
        return approvals.returnToSupervisor(
                principal.userId(),
                submissionId,
                new ReturnRequest(request.comments(), request.requestId()));
    }

    /** Approve request body. */
    public record ApproveBody(String comments, UUID requestId) {
    }

    /** Return request body. */
    public record ReturnBody(@NotBlank String comments, UUID requestId) {
    }
}
