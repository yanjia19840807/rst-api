package com.cmacgm.gbs.rst.api.approval.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.official.domain.OfficialPackage;
import com.cmacgm.gbs.rst.api.official.persistence.OfficialPackageRepository;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.ScenarioAssumption;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.submission.domain.Submission;
import com.cmacgm.gbs.rst.api.submission.persistence.SubmissionRepository;
import com.cmacgm.gbs.rst.api.workflow.application.DevWorkflowRouter;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowAction;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowStepAssignment;
import com.cmacgm.gbs.rst.api.workflow.persistence.WorkflowInstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Approver queue, review detail, Approve/Return, and Supervisor Withdraw.
 */
@Service
public class ApprovalService {

    private static final Set<String> OPEN_STATUSES = Set.of(
            "AWAITING_MANAGER", "AWAITING_CDH", "AWAITING_LTH");
    private static final Set<String> ARCHIVED_STATUSES = Set.of(
            "VALIDATED", "RETURNED", "ARCHIVED");

    private final SubmissionRepository submissions;
    private final WorkflowInstanceRepository workflows;
    private final OfficialPackageRepository packages;
    private final RstExerciseRepository exercises;
    private final ScenarioRepository scenarios;
    private final DevWorkflowRouter workflowRouter;
    private final Clock clock;

    /**
     * Creates the Approval service.
     *
     * @param submissions submission repository
     * @param workflows workflow repository
     * @param packages official package repository
     * @param exercises exercise repository
     * @param scenarios scenario repository
     * @param workflowRouter assignee router
     * @param clock clock
     */
    public ApprovalService(
            SubmissionRepository submissions,
            WorkflowInstanceRepository workflows,
            OfficialPackageRepository packages,
            RstExerciseRepository exercises,
            ScenarioRepository scenarios,
            DevWorkflowRouter workflowRouter,
            Clock clock) {
        this.submissions = submissions;
        this.workflows = workflows;
        this.packages = packages;
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.workflowRouter = workflowRouter;
        this.clock = clock;
    }

    /**
     * Lists submissions for the Approver queue.
     *
     * @param status optional filter; {@code AWAITING} (default) for open queue, ignored when archived
     * @param archived when true, list VALIDATED/RETURNED/ARCHIVED instead of open AWAITING_* rows
     * @return queue items
     */
    @Transactional(readOnly = true)
    public List<ApprovalQueueItem> queue(String status, boolean archived) {
        Set<String> statuses = archived ? ARCHIVED_STATUSES : resolveOpenFilter(status);
        List<ApprovalQueueItem> items = new ArrayList<>();
        for (Submission submission : submissions.findByStatusInOrderBySubmittedAtDesc(statuses)) {
            OfficialPackage pkg = packages.findById(submission.getOfficialPackageId()).orElse(null);
            if (pkg == null) {
                continue;
            }
            RstExercise exercise = exercises.findById(pkg.getExerciseId()).orElse(null);
            if (exercise == null) {
                continue;
            }
            WorkflowInstance workflow = workflows.findBySubmissionId(submission.getId()).orElse(null);
            final Short currentStep = workflow != null
                    ? workflow.getCurrentStep()
                    : submission.getCurrentStep();
            String requiredRole;
            if (workflow != null) {
                requiredRole = workflow.findCurrentReadyStep()
                        .map(WorkflowStepAssignment::getRequiredRoleCode)
                        .orElseGet(() -> roleForStep(currentStep));
            } else {
                requiredRole = roleForStep(currentStep);
            }
            var snapshot = exercise.getToolkitSnapshot();
            String toolkitName = snapshot != null ? snapshot.getToolkitName() : "";
            String pl3Name = snapshot != null ? snapshot.getPl3Name() : "";
            Instant archivedAt = exercise.getValidatedAt() != null
                    ? exercise.getValidatedAt()
                    : submission.getSubmittedAt();
            items.add(new ApprovalQueueItem(
                    submission.getId(),
                    exercise.getExerciseCode(),
                    pkg.getPackageVersion(),
                    currentStep,
                    requiredRole,
                    submission.getStatus(),
                    submission.getSubmittedAt(),
                    toolkitName,
                    pl3Name,
                    archivedAt));
        }
        return items;
    }

    /**
     * Returns Approver review detail for a submission (Submitted Details fields).
     *
     * @param submissionId submission id
     * @return review detail
     */
    @Transactional(readOnly = true)
    public ApprovalDetailView detail(UUID submissionId) {
        Loaded loaded = load(submissionId);
        return toDetail(loaded);
    }

    /**
     * Approves the current READY workflow step.
     *
     * <p>Step 1 adds CDH READY and moves submission to AWAITING_CDH; step 2 adds LTH READY and
     * AWAITING_LTH; step 3 completes workflow and validates submission/package/exercise.
     *
     * @param actorUserId acting principal
     * @param submissionId submission id
     * @param request approve payload
     * @return updated review detail
     */
    @Transactional
    public ApprovalDetailView approve(UUID actorUserId, UUID submissionId, ApproveRequest request) {
        Loaded loaded = load(submissionId);
        if (!"ACTIVE".equals(loaded.workflow().getStatus()) || !loaded.submission().isAwaitingReview()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "submission-not-awaiting",
                    "Submission is not awaiting approval.");
        }
        UUID requestId = request.requestId() == null ? UUID.randomUUID() : request.requestId();
        var existing = loaded.workflow().findActionByRequestId(requestId);
        if (existing.isPresent()) {
            return toDetail(loaded);
        }

        WorkflowStepAssignment current = loaded.workflow().findCurrentReadyStep()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "workflow-step-not-ready",
                        "Current workflow step is not READY."));
        Instant now = clock.instant();
        short stepNo = current.getStepNo();
        loaded.workflow().addAction(WorkflowAction.approve(
                stepNo,
                actorUserId,
                current.getRequiredRoleCode(),
                request.comments(),
                requestId,
                now));
        current.markActed();

        if (stepNo == 1) {
            String scopeHash = current.getScopeSnapshotHash();
            loaded.workflow().advanceAfterApprove(WorkflowStepAssignment.readyCdh(
                    workflowRouter.resolveCdhAssignee(), scopeHash, now));
            loaded.submission().advanceAfterApprove(stepNo, now);
        } else if (stepNo == 2) {
            String scopeHash = current.getScopeSnapshotHash();
            loaded.workflow().advanceAfterApprove(WorkflowStepAssignment.readyLth(
                    workflowRouter.resolveLthAssignee(), scopeHash, now));
            loaded.submission().advanceAfterApprove(stepNo, now);
        } else if (stepNo == 3) {
            loaded.workflow().complete(now);
            loaded.submission().advanceAfterApprove(stepNo, now);
            loaded.pkg().markValidated();
            loaded.exercise().markValidated(actorUserId, now);
        } else {
            throw new ApiException(
                    HttpStatus.CONFLICT, "unsupported-step", "Unsupported workflow step: " + stepNo);
        }

        persist(loaded);
        return toDetail(loaded);
    }

    /**
     * Returns the submission to the Supervisor with required comments.
     *
     * @param actorUserId acting principal
     * @param submissionId submission id
     * @param request return payload (comments required)
     * @return updated review detail
     */
    @Transactional
    public ApprovalDetailView returnToSupervisor(
            UUID actorUserId, UUID submissionId, ReturnRequest request) {
        if (request.comments() == null || request.comments().isBlank()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "comments-required",
                    "Return comments are required.");
        }
        Loaded loaded = load(submissionId);
        if (!"ACTIVE".equals(loaded.workflow().getStatus()) || !loaded.submission().isAwaitingReview()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "submission-not-awaiting",
                    "Submission is not awaiting approval.");
        }
        UUID requestId = request.requestId() == null ? UUID.randomUUID() : request.requestId();
        if (loaded.workflow().findActionByRequestId(requestId).isPresent()) {
            return toDetail(loaded);
        }

        WorkflowStepAssignment current = loaded.workflow().findCurrentReadyStep()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "workflow-step-not-ready",
                        "Current workflow step is not READY."));
        Instant now = clock.instant();
        loaded.workflow().addAction(WorkflowAction.returnAction(
                current.getStepNo(),
                actorUserId,
                current.getRequiredRoleCode(),
                request.comments(),
                requestId,
                now));
        current.markActed();
        loaded.workflow().markReturned(now);
        loaded.submission().markReturned(now);
        reopenAfterReturnOrWithdraw(loaded, actorUserId, now);
        persist(loaded);
        return toDetail(loaded);
    }

    /**
     * Withdraws an UNDER_REVIEW submission as Supervisor: cancels workflow and reopens Exercise.
     *
     * @param ownerId Supervisor owner id
     * @param exerciseId Exercise id
     * @return review detail after withdraw
     */
    @Transactional
    public ApprovalDetailView withdraw(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = exercises.findByIdAndOwnerUserIdAndDeletedAtIsNull(exerciseId, ownerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "exercise-not-found", "The Exercise was not found."));
        if (!exercise.canWithdraw()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-withdrawable",
                    "Only UNDER_REVIEW Exercises can be withdrawn.");
        }
        OfficialPackage pkg = packages.findByExerciseIdAndCurrentTrue(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "official-package-not-found",
                        "No current Official Package found for this Exercise."));
        Submission submission = submissions.findByOfficialPackageId(pkg.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "submission-not-found",
                        "No submission exists for this Exercise."));
        WorkflowInstance workflow = workflows.findBySubmissionId(submission.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "workflow-not-found",
                        "No workflow exists for this submission."));
        if (!"ACTIVE".equals(workflow.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "workflow-not-active",
                    "Workflow is not ACTIVE and cannot be withdrawn.");
        }

        Instant now = clock.instant();
        workflow.markCancelled(now);
        submission.markArchived(now);
        Loaded loaded = new Loaded(submission, workflow, pkg, exercise);
        reopenAfterReturnOrWithdraw(loaded, ownerId, now);
        persist(loaded);
        return toDetail(loaded);
    }

    private void reopenAfterReturnOrWithdraw(Loaded loaded, UUID actorUserId, Instant now) {
        loaded.pkg().markReturned();
        Scenario official = scenarios.findById(loaded.pkg().getScenarioId()).orElse(null);
        if (official != null && "OFFICIAL".equals(official.getStatus())) {
            String revisionCode = nextRevisionCode(loaded.exercise().getId(), official.getScenarioCode());
            Scenario draft = Scenario.supersedeOfficialAndCloneDraft(
                    official, revisionCode, actorUserId, now);
            List<ScenarioAssumption> copies = new ArrayList<>();
            for (ScenarioAssumption source : official.getAssumptions()) {
                copies.add(copyAssumption(source, actorUserId, now));
            }
            if (!copies.isEmpty()) {
                draft.replaceAssumptions(copies, actorUserId, now);
            }
            scenarios.save(official);
            scenarios.save(draft);
        } else if (official != null) {
            official.markSuperseded(actorUserId, now);
            scenarios.save(official);
        }
        loaded.exercise().markReturned(actorUserId, now);
    }

    private String nextRevisionCode(UUID exerciseId, String baseCode) {
        String candidate = baseCode + "-R1";
        int revision = 1;
        while (scenarios.existsByExerciseIdAndScenarioCodeAndDeletedAtIsNull(exerciseId, candidate)) {
            revision++;
            candidate = baseCode + "-R" + revision;
        }
        return candidate;
    }

    private static ScenarioAssumption copyAssumption(
            ScenarioAssumption source, UUID actorUserId, Instant now) {
        if (source.getNumericValue() != null) {
            return ScenarioAssumption.numeric(
                    source.getParameterCode(),
                    source.getNumericValue(),
                    source.getUnit(),
                    actorUserId,
                    now);
        }
        if (source.getTextValue() != null) {
            return ScenarioAssumption.text(
                    source.getParameterCode(), source.getTextValue(), actorUserId, now);
        }
        if (source.getBooleanValue() != null) {
            return ScenarioAssumption.bool(
                    source.getParameterCode(), source.getBooleanValue(), actorUserId, now);
        }
        return ScenarioAssumption.text(source.getParameterCode(), "", actorUserId, now);
    }

    private Loaded load(UUID submissionId) {
        Submission submission = submissions.findById(submissionId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "submission-not-found", "The Submission was not found."));
        WorkflowInstance workflow = workflows.findBySubmissionId(submissionId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "workflow-not-found", "No workflow exists for this submission."));
        OfficialPackage pkg = packages.findById(submission.getOfficialPackageId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "official-package-not-found", "Official Package not found."));
        RstExercise exercise = exercises.findById(pkg.getExerciseId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "exercise-not-found", "The Exercise was not found."));
        return new Loaded(submission, workflow, pkg, exercise);
    }

    private void persist(Loaded loaded) {
        submissions.save(loaded.submission());
        workflows.save(loaded.workflow());
        packages.save(loaded.pkg());
        exercises.save(loaded.exercise());
    }

    private ApprovalDetailView toDetail(Loaded loaded) {
        String scenarioName = scenarios.findById(loaded.pkg().getScenarioId())
                .map(Scenario::getName)
                .orElse(null);
        List<ScopeView> scopes = loaded.submission().getScopes().stream()
                .map(s -> new ScopeView(
                        s.getScopeLevel(), s.getCenter(), s.getSite(), s.getDomain(),
                        s.getPl3Code(), s.getCarrier(), s.getCustomerCountry()))
                .toList();
        List<StepView> steps = loaded.workflow().getSteps().stream()
                .map(s -> new StepView(
                        s.getStepNo(), s.getRequiredRoleCode(), s.getAssigneeUserId(),
                        s.getRoutingStatus()))
                .toList();
        List<ActionView> actions = loaded.workflow().getActions().stream()
                .map(a -> new ActionView(
                        a.getStepNo(),
                        a.getActionType(),
                        a.getActorUserId(),
                        a.getActorRoleCode(),
                        a.getComments(),
                        a.getActionAt(),
                        a.getRequestId()))
                .toList();
        String requiredRole = loaded.workflow().findCurrentReadyStep()
                .map(WorkflowStepAssignment::getRequiredRoleCode)
                .orElseGet(() -> roleForStep(loaded.workflow().getCurrentStep()));
        return new ApprovalDetailView(
                loaded.exercise().getId(),
                loaded.exercise().getExerciseCode(),
                loaded.exercise().getWorkflowStatus(),
                loaded.exercise().getSubmittedAt(),
                loaded.pkg().getId(),
                loaded.pkg().getPackageVersion(),
                loaded.pkg().getStatus(),
                loaded.pkg().getScenarioId(),
                scenarioName,
                loaded.submission().getId(),
                loaded.submission().getSubmissionCode(),
                loaded.submission().getStatus(),
                loaded.submission().getCurrentStep(),
                requiredRole,
                loaded.submission().getRemarks(),
                scopes,
                loaded.workflow().getId(),
                loaded.workflow().getStatus(),
                steps,
                actions);
    }

    private static Set<String> resolveOpenFilter(String status) {
        if (status == null || status.isBlank() || "AWAITING".equalsIgnoreCase(status)) {
            return OPEN_STATUSES;
        }
        String normalized = status.trim().toUpperCase();
        if (OPEN_STATUSES.contains(normalized)) {
            return Set.of(normalized);
        }
        if (normalized.startsWith("AWAITING")) {
            return OPEN_STATUSES;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "invalid-status-filter",
                "status must be AWAITING or a specific AWAITING_* value.");
    }

    private static String roleForStep(Short step) {
        if (step == null) {
            return null;
        }
        return switch (step) {
            case 1 -> "MANAGER";
            case 2 -> "CDH";
            case 3 -> "LTH";
            default -> null;
        };
    }

    private record Loaded(
            Submission submission,
            WorkflowInstance workflow,
            OfficialPackage pkg,
            RstExercise exercise) {
    }

    /** Approver queue row. */
    public record ApprovalQueueItem(
            UUID submissionId,
            String exerciseCode,
            int packageVersion,
            Short currentStep,
            String requiredRole,
            String status,
            Instant submittedAt,
            String toolkitName,
            String pl3Name,
            Instant archivedAt) {
    }

    /** Approver review detail (extends Submitted Details fields). */
    public record ApprovalDetailView(
            UUID exerciseId,
            String exerciseCode,
            String workflowStatus,
            Instant submittedAt,
            UUID officialPackageId,
            int packageVersion,
            String packageStatus,
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
            List<ActionView> actions) {
    }

    /** Submission scope view. */
    public record ScopeView(
            String scopeLevel, String center, String site, String domain, String pl3Code,
            String carrier, String customerCountry) {
    }

    /** Workflow step view. */
    public record StepView(
            short stepNo, String requiredRoleCode, UUID assigneeUserId, String routingStatus) {
    }

    /** Workflow action view. */
    public record ActionView(
            short stepNo,
            String actionType,
            UUID actorUserId,
            String actorRoleCode,
            String comments,
            Instant actionAt,
            UUID requestId) {
    }

    /** Approve request payload. */
    public record ApproveRequest(String comments, UUID requestId) {
    }

    /** Return request payload. */
    public record ReturnRequest(String comments, UUID requestId) {
    }
}
