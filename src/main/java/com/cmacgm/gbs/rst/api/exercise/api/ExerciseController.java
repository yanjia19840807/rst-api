package com.cmacgm.gbs.rst.api.exercise.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.approval.application.ApprovalService;
import com.cmacgm.gbs.rst.api.approval.application.ApprovalService.ApprovalDetailView;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService.CreateExercise;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService.Exercise;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.submission.application.SubmissionService;
import com.cmacgm.gbs.rst.api.submission.application.SubmissionService.SubmitPreviewView;
import com.cmacgm.gbs.rst.api.submission.application.SubmissionService.SubmitRequest;
import com.cmacgm.gbs.rst.api.submission.application.SubmissionService.SubmittedDetailsView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supervisor Exercise list/create/detail/delete and Submit endpoints.
 */
@RestController
@RequestMapping("/api/v1/supervisor/exercises")
@PreAuthorize("hasRole('SUPERVISOR')")
public class ExerciseController {

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
    public ExerciseController(
            ExerciseService service, SubmissionService submissions, ApprovalService approvals) {
        this.service = service;
        this.submissions = submissions;
        this.approvals = approvals;
    }

    /**
     * Lists Exercises for the current Supervisor.
     *
     * @param principal authenticated Supervisor
     * @return exercises
     */
    @GetMapping
    public List<Exercise> list(@AuthenticationPrincipal RstPrincipal principal) {
        return service.list(principal.userId());
    }

    /**
     * Creates an Exercise with empty Associated Data shells.
     *
     * @param principal authenticated Supervisor
     * @param request create payload
     * @return created Exercise
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Exercise create(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody CreateExercise request) {
        return service.create(principal.userId(), principal.ccgid(), request);
    }

    /**
     * Returns Exercise detail including Official/submit flags.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     * @return Exercise detail
     */
    @GetMapping("/{id}")
    public Exercise detail(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return service.detail(principal.userId(), id);
    }

    /**
     * Soft-deletes an unsubmitted Exercise.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        service.softDelete(principal.userId(), id);
    }

    /**
     * Previews Submit validations for the current Official Package.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     * @return submit preview
     */
    @PostMapping("/{id}/validations/submit-preview")
    public SubmitPreviewView submitPreview(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return submissions.submitPreview(principal.userId(), id);
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
    public SubmittedDetailsView submit(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) SubmitRequest request) {
        SubmitRequest payload = request == null ? new SubmitRequest(null, null) : request;
        return submissions.submit(principal.userId(), id, payload);
    }

    /**
     * Returns Submitted Details for a submitted Exercise.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     * @return submitted details
     */
    @GetMapping("/{id}/submitted-details")
    public SubmittedDetailsView submittedDetails(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return submissions.submittedDetails(principal.userId(), id);
    }

    /**
     * Withdraws an UNDER_REVIEW submission, cancelling the workflow and reopening the Exercise.
     *
     * @param principal authenticated Supervisor
     * @param id Exercise id
     * @return review detail after withdraw
     */
    @PostMapping("/{id}/withdraw")
    public ApprovalDetailView withdraw(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return approvals.withdraw(principal.userId(), id);
    }
}
