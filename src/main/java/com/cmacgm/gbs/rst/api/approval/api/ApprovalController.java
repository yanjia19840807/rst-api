package com.cmacgm.gbs.rst.api.approval.api;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import com.cmacgm.gbs.rst.api.approval.application.ApprovalService;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalDetailView;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalQueueView;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApproveRequest;
import com.cmacgm.gbs.rst.api.approval.api.dto.QueueQuery;
import com.cmacgm.gbs.rst.api.approval.api.dto.ReturnRequest;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
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
     * Lists submissions awaiting a Timesheet position the caller occupies, or completed
     * by that position when {@code completed=true}. The User who acted is on each action.
     *
     * @param status optional status filter; default {@code AWAITING} for open queue
     * @param completed when true, list tasks this position has already Approved or Returned
     * @param exerciseCode optional exercise code contains
     * @param toolkitName optional exact toolkit name
     * @param pl3Name optional exact PL3 name
     * @param submittedFrom optional submitted date from
     * @param submittedTo optional submitted date to
     * @param completedFrom optional position-completion date from
     * @param completedTo optional position-completion date to
     * @param decision optional Approved / Returned (this position's decision)
     * @param principal current approver
     * @return filtered queue, filter options, and awaiting metrics
     */
    @GetMapping("/queue")
    public ApprovalQueueView queue(
            @RequestParam(required = false, defaultValue = "AWAITING") String status,
            @RequestParam(required = false, defaultValue = "false") boolean completed,
            @RequestParam(required = false) String exerciseCode,
            @RequestParam(required = false) String toolkitName,
            @RequestParam(required = false) String pl3Name,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedTo,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate completedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate completedTo,
            @RequestParam(required = false) String decision,
            @AuthenticationPrincipal RstPrincipal principal) {
        return approvals.queue(principal, new QueueQuery(
                status,
                completed,
                exerciseCode,
                toolkitName,
                pl3Name,
                submittedFrom,
                submittedTo,
                completedFrom,
                completedTo,
                decision));
    }

    /**
     * Returns review detail for a submission, including {@code canDecide} for the caller's position.
     *
     * @param principal current approver
     * @param submissionId submission id
     * @return review detail
     */
    @GetMapping("/{submissionId}")
    public ApprovalDetailView detail(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID submissionId) {
        return approvals.detail(principal, submissionId);
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
                principal,
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
                principal,
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
