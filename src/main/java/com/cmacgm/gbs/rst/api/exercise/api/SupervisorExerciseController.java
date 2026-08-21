package com.cmacgm.gbs.rst.api.exercise.api;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.approval.application.ApprovalService;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalDetailView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.CreateExerciseRequest;
import com.cmacgm.gbs.rst.api.exercise.api.dto.CreateExerciseResult;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseListQuery;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseListView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseResponse;
import com.cmacgm.gbs.rst.api.exercise.api.dto.UpdateExercisePeriodsRequest;
import com.cmacgm.gbs.rst.api.exercise.api.dto.UpdateExercisePeriodsResult;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.submission.application.SubmissionService;
import com.cmacgm.gbs.rst.api.submission.api.dto.SubmitPreviewView;
import com.cmacgm.gbs.rst.api.submission.api.dto.SubmitRequest;
import com.cmacgm.gbs.rst.api.submission.api.dto.SubmittedDetailsView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supervisor Exercise list/create/detail/delete and Submit endpoints.
 */
@RestController
@RequestMapping("/api/v1/supervisor/exercises")
public class SupervisorExerciseController {

    private final ExerciseService service;
    private final SubmissionService submissions;
    private final ApprovalService approvals;

    /**
     * Creates the Exercise controller.
     *
     * @param service Exercise service
     * @param submissions Submission service
     * @param approvals Approval service (Withdraw)
     */
    public SupervisorExerciseController(
            ExerciseService service, SubmissionService submissions, ApprovalService approvals) {
        this.service = service;
        this.submissions = submissions;
        this.approvals = approvals;
    }

    /**
     * Lists Exercises for the current Supervisor, applying tab and field filters on the server.
     *
     * @param tab {@code IN_PROGRESS} or {@code ARCHIVED}
     * @param exerciseCode optional exercise code contains
     * @param toolkitName optional exact toolkit name
     * @param pl3Name optional exact PL3 name
     * @param workflowStatus optional exact workflow status within the tab
     * @param reviewStage optional required role ({@code MANAGER} / {@code CDH} / {@code LTH})
     * @param handler optional current reviewer display name
     * @param officialScenario {@code ASSIGNED} or {@code UNASSIGNED}
     * @param createdFrom optional created date from
     * @param createdTo optional created date to
     * @param submittedFrom optional submitted date from
     * @param submittedTo optional submitted date to
     * @param archivedFrom optional archived date from
     * @param archivedTo optional archived date to
     * @param page 1-based page
     * @param pageSize page size
     * @param principal authenticated Supervisor
     * @return one page of filtered exercises and filter options
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ExerciseListView list(
            @RequestParam(required = false, defaultValue = "IN_PROGRESS") String tab,
            @RequestParam(required = false) String exerciseCode,
            @RequestParam(required = false) String toolkitName,
            @RequestParam(required = false) String pl3Name,
            @RequestParam(required = false) String workflowStatus,
            @RequestParam(required = false) String reviewStage,
            @RequestParam(required = false) String handler,
            @RequestParam(required = false) String officialScenario,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate createdFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate createdTo,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedTo,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate archivedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate archivedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal RstPrincipal principal) {
        return service.list(principal.ccgid(), new ExerciseListQuery(
                tab,
                exerciseCode,
                toolkitName,
                pl3Name,
                workflowStatus,
                reviewStage,
                handler,
                officialScenario,
                createdFrom,
                createdTo,
                submittedFrom,
                submittedTo,
                archivedFrom,
                archivedTo), page, pageSize);
    }

    /**
     * Creates an Exercise and seeds Associated Data (archive-first copy).
     *
     * @param principal authenticated Supervisor
     * @param request create payload
     * @return created Exercise and initialization notices
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public CreateExerciseResult create(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody CreateExerciseRequest request) {
        return service.create(principal.ccgid(), request);
    }

    /**
     * Returns Exercise detail including Official/submit flags.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     * @return Exercise detail
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR','MANAGER','CDH','LTH')")
    public ExerciseResponse detail(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return service.detail(principal.ccgid(), id);
    }

    /**
     * Soft-deletes an unsubmitted Exercise.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public void delete(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        service.softDelete(principal.ccgid(), id);
    }

    /**
     * Updates sizing / slot / TMS periods on an editable Exercise.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     * @param request period payload
     * @return updated Exercise and notices
     */
    @PutMapping("/{id}/periods")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public UpdateExercisePeriodsResult updatePeriods(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExercisePeriodsRequest request) {
        return service.updatePeriods(principal.ccgid(), id, request);
    }

    /**
     * Previews Submit validations for the current Official Package.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     * @return submit preview
     */
    @PostMapping("/{id}/validations/submit-preview")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public SubmitPreviewView submitPreview(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return submissions.submitPreview(principal.ccgid(), id);
    }

    /**
     * Submits the current Official Package for Manager validation.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     * @param request submit payload
     * @return submitted details
     */
    @PostMapping("/{id}/submit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public SubmittedDetailsView submit(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) SubmitRequest request) {
        SubmitRequest payload = request == null ? new SubmitRequest(null, null) : request;
        return submissions.submit(principal.ccgid(), id, payload);
    }

    /**
     * Returns Submitted Details for a submitted Exercise.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     * @return submitted details
     */
    @GetMapping("/{id}/submitted-details")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public SubmittedDetailsView submittedDetails(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return submissions.submittedDetails(principal.ccgid(), id);
    }

    /**
     * Withdraws an UNDER_REVIEW submission, cancelling the workflow and reopening the Exercise.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     * @return review detail after withdraw
     */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ApprovalDetailView withdraw(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return approvals.withdraw(principal.ccgid(), id);
    }
}
