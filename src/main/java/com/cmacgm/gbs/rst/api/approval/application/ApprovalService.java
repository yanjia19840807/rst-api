package com.cmacgm.gbs.rst.api.approval.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService;
import com.cmacgm.gbs.rst.api.identity.domain.AppUser;
import com.cmacgm.gbs.rst.api.identity.persistence.AppUserRepository;
import com.cmacgm.gbs.rst.api.official.domain.OfficialPackage;
import com.cmacgm.gbs.rst.api.official.persistence.OfficialPackageRepository;
import com.cmacgm.gbs.rst.api.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.ScenarioAssumption;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.submission.domain.Submission;
import com.cmacgm.gbs.rst.api.submission.persistence.SubmissionRepository;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
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
    private static final Set<String> QUEUE_STATUSES = Set.of(
            "AWAITING_MANAGER", "AWAITING_CDH", "AWAITING_LTH",
            "VALIDATED", "RETURNED", "ARCHIVED");

    private final SubmissionRepository submissions;
    private final WorkflowInstanceRepository workflows;
    private final OfficialPackageRepository packages;
    private final RstExerciseRepository exercises;
    private final ScenarioRepository scenarios;
    private final ExerciseTeamSetupRepository teamSetups;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final HolidayTemplateService holidayTemplates;
    private final AppUserRepository users;
    private final WorkflowRouter workflowRouter;
    private final ApprovalWorkspaceAssembler workspaceAssembler;
    private final Clock clock;

    /**
     * Creates the Approval service.
     */
    public ApprovalService(
            SubmissionRepository submissions,
            WorkflowInstanceRepository workflows,
            OfficialPackageRepository packages,
            RstExerciseRepository exercises,
            ScenarioRepository scenarios,
            ExerciseTeamSetupRepository teamSetups,
            ExerciseProductionSupportItemRepository supportItems,
            HolidayTemplateService holidayTemplates,
            AppUserRepository users,
            WorkflowRouter workflowRouter,
            ApprovalWorkspaceAssembler workspaceAssembler,
            Clock clock) {
        this.submissions = submissions;
        this.workflows = workflows;
        this.packages = packages;
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.teamSetups = teamSetups;
        this.supportItems = supportItems;
        this.holidayTemplates = holidayTemplates;
        this.users = users;
        this.workflowRouter = workflowRouter;
        this.workspaceAssembler = workspaceAssembler;
        this.clock = clock;
    }

    /**
     * Lists submissions for the Approver queue, applying the given filters on the server.
     *
     * <p>Awaiting Review only includes submissions whose current READY step is assigned
     * to a Timesheet position the caller occupies. Completed Task includes submissions
     * where that position has already Approved or Returned. The User who acted is stored
     * on {@code actor_user_id}.
     *
     * @param principal current approver
     * @param query tab, status, and field filters
     * @return filtered rows, filter options, and awaiting-me metrics
     */
    @Transactional(readOnly = true)
    public ApprovalQueueView queue(RstPrincipal principal, QueueQuery query) {
        if (principal == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication is required.");
        }
        Set<String> myPositions = workflowRouter.positionsFor(principal);
        List<ApprovalQueueItem> awaiting = listItems(
                resolveOpenFilter(query.status()), myPositions, true, true);
        List<ApprovalQueueItem> source;
        if (query.completed()) {
            source = listItems(QUEUE_STATUSES, myPositions, false, false).stream()
                    .sorted(Comparator.comparing(
                            ApprovalQueueItem::myCompletedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } else {
            source = awaiting;
        }
        List<ApprovalQueueItem> items = source.stream()
                .filter(item -> matches(item, query))
                .toList();
        return new ApprovalQueueView(
                items,
                toMetrics(awaiting),
                distinctNames(source, ApprovalQueueItem::toolkitName),
                distinctNames(source, ApprovalQueueItem::pl3Name));
    }

    private List<ApprovalQueueItem> listItems(
            Set<String> statuses, Set<String> myPositions, boolean awaitingOnly, boolean byAging) {
        Map<UUID, String> names = new HashMap<>();
        List<ApprovalQueueItem> items = new ArrayList<>();
        for (Submission submission : submissions.findByStatusInOrderBySubmittedAtDesc(statuses)) {
            OfficialPackage pkg = packages.findById(submission.getOfficialPackageId()).orElse(null);
            if (pkg == null) {
                continue;
            }
            RstExercise exercise = exercises.findById(pkg.getExerciseId()).orElse(null);
            if (exercise == null || exercise.getDeletedAt() != null) {
                continue;
            }
            WorkflowInstance workflow = workflows.findBySubmissionId(submission.getId()).orElse(null);
            boolean awaitingMe = isAwaitingMyPosition(submission, workflow, exercise, myPositions);
            WorkflowAction mine = positionDecision(workflow, exercise, myPositions);
            if (awaitingOnly && !awaitingMe) {
                continue;
            }
            if (!awaitingOnly && (mine == null || awaitingMe)) {
                continue;
            }
            items.add(toQueueItem(submission, pkg, exercise, workflow, names, mine));
        }
        if (byAging) {
            items.sort(Comparator.comparing(
                    ApprovalQueueItem::agingDays, Comparator.nullsLast(Comparator.reverseOrder())));
        }
        return items;
    }

    private static QueueMetrics toMetrics(List<ApprovalQueueItem> awaiting) {
        int overdue = 0;
        int dueSoon = 0;
        int highRisk = 0;
        for (ApprovalQueueItem item : awaiting) {
            int days = item.agingDays() == null ? 0 : item.agingDays();
            if (days >= 5) {
                overdue++;
                highRisk++;
            } else if (days >= 3) {
                dueSoon++;
            }
        }
        return new QueueMetrics(awaiting.size(), overdue, dueSoon, highRisk);
    }

    private static List<String> distinctNames(
            List<ApprovalQueueItem> items, Function<ApprovalQueueItem, String> getter) {
        return items.stream()
                .map(getter)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean matches(ApprovalQueueItem item, QueueQuery query) {
        if (hasText(query.exerciseCode())) {
            String code = item.exerciseCode() == null ? "" : item.exerciseCode();
            if (!code.toLowerCase(Locale.ROOT)
                    .contains(query.exerciseCode().trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (hasText(query.toolkitName()) && !query.toolkitName().equals(item.toolkitName())) {
            return false;
        }
        if (hasText(query.pl3Name()) && !query.pl3Name().equals(item.pl3Name())) {
            return false;
        }
        LocalDate submitted = dateOf(item.submittedAt());
        if (query.submittedFrom() != null
                && (submitted == null || submitted.isBefore(query.submittedFrom()))) {
            return false;
        }
        if (query.submittedTo() != null
                && (submitted == null || submitted.isAfter(query.submittedTo()))) {
            return false;
        }
        LocalDate completedAt = dateOf(item.myCompletedAt());
        if (query.completedFrom() != null
                && (completedAt == null || completedAt.isBefore(query.completedFrom()))) {
            return false;
        }
        if (query.completedTo() != null
                && (completedAt == null || completedAt.isAfter(query.completedTo()))) {
            return false;
        }
        if (hasText(query.decision()) && !query.decision().equals(item.myDecision())) {
            return false;
        }
        return true;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static LocalDate dateOf(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * Returns Approver review detail for a submission (Submitted Details fields).
     *
     * @param principal current approver (used to set {@code canDecide} from position ownership)
     * @param submissionId submission id
     * @return review detail
     */
    @Transactional(readOnly = true)
    public ApprovalDetailView detail(RstPrincipal principal, UUID submissionId) {
        Loaded loaded = load(submissionId);
        return toDetail(loaded, principal);
    }

    /**
     * Approves the current READY workflow step.
     *
     * <p>Step 1 adds CDH READY and moves submission to AWAITING_CDH; step 2 adds LTH READY and
     * AWAITING_LTH; step 3 completes workflow and validates submission/package/exercise.
     *
     * @param principal acting approver
     * @param submissionId submission id
     * @param request approve payload
     * @return updated review detail
     */
    @Transactional
    public ApprovalDetailView approve(RstPrincipal principal, UUID submissionId, ApproveRequest request) {
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
            return toDetail(loaded, principal);
        }

        WorkflowStepAssignment current = loaded.workflow().findCurrentReadyStep()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "workflow-step-not-ready",
                        "Current workflow step is not READY."));
        requireCurrentReviewer(principal, current, loaded.exercise());
        Instant now = clock.instant();
        short stepNo = current.getStepNo();
        UUID actorUserId = principal.userId();
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
            WorkflowRouter.RoutedStep cdh = workflowRouter.resolveCdh(supervisorPosition(loaded.exercise()));
            loaded.workflow().advanceAfterApprove(WorkflowStepAssignment.readyCdh(
                    cdh.occupantUserId(), cdh.positionId(), scopeHash, now));
            loaded.submission().advanceAfterApprove(stepNo, now);
        } else if (stepNo == 2) {
            String scopeHash = current.getScopeSnapshotHash();
            WorkflowRouter.RoutedStep lth = workflowRouter.resolveLth();
            loaded.workflow().advanceAfterApprove(WorkflowStepAssignment.readyLth(
                    lth.occupantUserId(), lth.positionId(), scopeHash, now));
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
        return toDetail(loaded, principal);
    }

    /**
     * Returns the submission to the Supervisor with required comments.
     *
     * @param principal acting approver
     * @param submissionId submission id
     * @param request return payload (comments required)
     * @return updated review detail
     */
    @Transactional
    public ApprovalDetailView returnToSupervisor(
            RstPrincipal principal, UUID submissionId, ReturnRequest request) {
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
            return toDetail(loaded, principal);
        }

        WorkflowStepAssignment current = loaded.workflow().findCurrentReadyStep()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "workflow-step-not-ready",
                        "Current workflow step is not READY."));
        requireCurrentReviewer(principal, current, loaded.exercise());
        Instant now = clock.instant();
        UUID actorUserId = principal.userId();
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
        reopenExercise(loaded, actorUserId, now, true);
        persist(loaded);
        return toDetail(loaded, principal);
    }

    /**
     * Withdraws an UNDER_REVIEW submission as Supervisor: cancels workflow, reverts the
     * Official scenario back to DRAFT, and reopens the Exercise for editing.
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
        short stepNo = workflow.findCurrentReadyStep()
                .map(WorkflowStepAssignment::getStepNo)
                .orElseGet(() -> workflow.getCurrentStep() == null
                        ? 0
                        : workflow.getCurrentStep());
        workflow.addAction(WorkflowAction.withdraw(stepNo, ownerId, UUID.randomUUID(), now));
        workflow.markCancelled(now);
        submission.markArchived(now);
        Loaded loaded = new Loaded(submission, workflow, pkg, exercise);
        reopenExercise(loaded, ownerId, now, false);
        persist(loaded);
        return toDetail(loaded, null);
    }

    /**
     * Reopens the Exercise after Return or Withdraw: Official Scenario is reverted to DRAFT
     * so simulations can run, but the Official pointer and current package stay so the same
     * approval submission can continue.
     */
    private void reopenExercise(Loaded loaded, UUID actorUserId, Instant now, boolean returned) {
        loaded.pkg().markReturned();
        Scenario official = scenarios.findById(loaded.pkg().getScenarioId()).orElse(null);
        if (official != null && "OFFICIAL".equals(official.getStatus())) {
            official.revertToDraft(actorUserId, now);
            scenarios.save(official);
        }
        if (returned) {
            loaded.exercise().markReturned(actorUserId, now);
        } else {
            loaded.exercise().markWithdrawn(actorUserId, now);
        }
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

    private ApprovalDetailView toDetail(Loaded loaded, RstPrincipal principal) {
        String scenarioName = scenarios.findById(loaded.pkg().getScenarioId())
                .map(Scenario::getName)
                .orElse(null);
        Map<UUID, String> displayNames = displayNames(loaded.workflow());
        String supervisorPositionId = supervisorPosition(loaded.exercise());
        List<ScopeView> scopes = loaded.submission().getScopes().stream()
                .map(s -> new ScopeView(
                        s.getScopeLevel(), s.getCenter(), s.getSite(), s.getDomain(),
                        s.getPl3Code(), s.getCarrier(), s.getCustomerCountry()))
                .toList();
        List<StepView> steps = loaded.workflow().getSteps().stream()
                .map(s -> toStepView(s, displayNames, supervisorPositionId))
                .toList();
        List<ActionView> actions = loaded.workflow().getActions().stream()
                .map(a -> new ActionView(
                        a.getStepNo(),
                        a.getActionType(),
                        a.getActorUserId(),
                        a.getActorRoleCode(),
                        displayNames.get(a.getActorUserId()),
                        a.getComments(),
                        a.getActionAt(),
                        a.getRequestId()))
                .toList();
        String requiredRole = loaded.workflow().findCurrentReadyStep()
                .map(WorkflowStepAssignment::getRequiredRoleCode)
                .orElseGet(() -> roleForStep(loaded.workflow().getCurrentStep()));
        boolean canDecide = loaded.submission().isAwaitingReview()
                && loaded.workflow().findCurrentReadyStep()
                        .filter(step -> ownsStep(principal, step, loaded.exercise()))
                        .isPresent();
        ApprovalWorkspaceView workspace = canDecide
                ? workspaceAssembler.inProgress(
                        loaded.submission(),
                        loaded.workflow(),
                        loaded.exercise(),
                        displayNames)
                : workspaceAssembler.completed(
                        loaded.submission(),
                        loaded.workflow(),
                        loaded.exercise(),
                        principal,
                        displayNames);
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
                actions,
                canDecide,
                workspace);
    }

    private StepView toStepView(
            WorkflowStepAssignment step,
            Map<UUID, String> displayNames,
            String supervisorPositionId) {
        String positionId = resolveStepPosition(step, supervisorPositionId);
        String liveName = workflowRouter.occupantName(step.getRequiredRoleCode(), positionId);
        String name = liveName != null
                ? liveName
                : (step.getAssigneeUserId() == null ? null : displayNames.get(step.getAssigneeUserId()));
        return new StepView(
                step.getStepNo(),
                step.getRequiredRoleCode(),
                step.getAssigneeUserId(),
                positionId,
                name,
                step.getRoutingStatus());
    }

    private ApprovalQueueItem toQueueItem(
            Submission submission,
            OfficialPackage pkg,
            RstExercise exercise,
            WorkflowInstance workflow,
            Map<UUID, String> names,
            WorkflowAction mine) {
        var snapshot = exercise.getToolkitSnapshot();
        String toolkitName = snapshot != null ? snapshot.getToolkitName() : "";
        String pl3Name = snapshot != null ? snapshot.getPl3Name() : "";
        String center = snapshot != null ? snapshot.getCenter() : "";
        String domain = snapshot != null ? snapshot.getDomain() : "";
        String supervisor = displayName(exercise.getOwnerUserId(), names);

        BigDecimal deliveryHc = deliveryHc(exercise);
        BigDecimal rightSizingHc = rightSizingHc(pkg.getScenarioId());
        BigDecimal productionSupport = productionSupport(exercise.getId());
        BigDecimal capacityCreation = rightSizingHc == null
                ? null
                : SizingMath.capacityCreation(deliveryHc, rightSizingHc, productionSupport);

        WorkflowAction previous = previousReviewAction(workflow);
        WorkflowAction last = lastCompletedAction(workflow);
        String previousStep = previous == null ? null : reviewStageLabel(previous.getActorRoleCode());
        String previousActor = last != null
                ? displayName(last.getActorUserId(), names)
                : supervisor;
        Instant previousStepAt = last != null ? last.getActionAt() : submission.getSubmittedAt();
        Instant agingFrom = previous != null ? previous.getActionAt() : submission.getSubmittedAt();
        int agingDays = daysBetween(agingFrom, clock.instant());

        Instant archivedAt = archivedAt(exercise, workflow, submission);
        Integer reviewDurationDays = ARCHIVED_STATUSES.contains(submission.getStatus())
                ? daysBetween(submission.getSubmittedAt(), archivedAt)
                : null;
        String myDecision = mine == null ? null : decisionLabel(mine.getActionType());
        Instant myCompletedAt = mine == null ? null : mine.getActionAt();
        String completedStep = mine == null ? null : reviewStageLabel(mine.getActorRoleCode());

        return new ApprovalQueueItem(
                submission.getId(),
                exercise.getId(),
                exercise.getExerciseCode(),
                center,
                domain,
                pl3Name,
                toolkitName,
                supervisor,
                deliveryHc,
                rightSizingHc,
                productionSupport,
                capacityCreation,
                previousStep,
                previousActor,
                previousStepAt,
                agingDays,
                exercise.getCreatedAt(),
                submission.getSubmittedAt(),
                archivedAt,
                finalStatus(submission.getStatus()),
                reviewDurationDays,
                submission.getStatus(),
                myDecision,
                myCompletedAt,
                completedStep);
    }

    private BigDecimal deliveryHc(RstExercise exercise) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ExerciseSharedKpiLine line : exercise.getSharedKpiLines()) {
            if (line.getDeliveryHc() != null) {
                sum = sum.add(line.getDeliveryHc());
            }
        }
        return sum;
    }

    private BigDecimal rightSizingHc(UUID scenarioId) {
        if (scenarioId == null) {
            return null;
        }
        Scenario scenario = scenarios.findById(scenarioId).orElse(null);
        if (scenario == null) {
            return null;
        }
        for (ScenarioAssumption assumption : scenario.getAssumptions()) {
            if ("RIGHT_SIZING_HC".equals(assumption.getParameterCode())
                    && assumption.getNumericValue() != null) {
                return assumption.getNumericValue();
            }
        }
        return null;
    }

    private BigDecimal productionSupport(UUID exerciseId) {
        List<ExerciseProductionSupportItem> items =
                supportItems.findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(exerciseId);
        if (items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        ExerciseTeamSetup setup = teamSetups.findById(exerciseId).orElse(null);
        BigDecimal workingDays = holidayTemplates.workingDaysPerYear(exerciseId);
        BigDecimal fteHours = SupportWorkloadMath.fteAnnualHours(setup, workingDays);
        BigDecimal total = BigDecimal.ZERO;
        for (ExerciseProductionSupportItem item : items) {
            try {
                total = total.add(SupportWorkloadMath.derive(item, workingDays, fteHours).supportFte());
            } catch (IllegalArgumentException ignored) {
                // skip rows with an unexpected frequency until Associated Data is corrected
            }
        }
        return total;
    }

    private static WorkflowAction previousReviewAction(WorkflowInstance workflow) {
        if (workflow == null) {
            return null;
        }
        return workflow.getActions().stream()
                .filter(action -> "APPROVE".equals(action.getActionType()))
                .max(Comparator.comparing(WorkflowAction::getActionAt))
                .orElse(null);
    }

    private static WorkflowAction lastCompletedAction(WorkflowInstance workflow) {
        if (workflow == null) {
            return null;
        }
        return workflow.getActions().stream()
                .filter(action -> "SUBMIT".equals(action.getActionType())
                        || "APPROVE".equals(action.getActionType()))
                .max(Comparator.comparing(WorkflowAction::getActionAt))
                .orElse(null);
    }

    private static Instant archivedAt(
            RstExercise exercise, WorkflowInstance workflow, Submission submission) {
        if (exercise.getValidatedAt() != null) {
            return exercise.getValidatedAt();
        }
        if (workflow != null) {
            return workflow.getActions().stream()
                    .filter(action -> "RETURN".equals(action.getActionType())
                            || "WITHDRAW".equals(action.getActionType()))
                    .map(WorkflowAction::getActionAt)
                    .max(Instant::compareTo)
                    .orElse(exercise.getUpdatedAt());
        }
        return exercise.getUpdatedAt() != null ? exercise.getUpdatedAt() : submission.getSubmittedAt();
    }

    private static String finalStatus(String submissionStatus) {
        if ("VALIDATED".equals(submissionStatus)) {
            return "Approved";
        }
        if ("RETURNED".equals(submissionStatus) || "ARCHIVED".equals(submissionStatus)) {
            return "Rejected";
        }
        return null;
    }

    private static int daysBetween(Instant from, Instant to) {
        if (from == null || to == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(
                from.atZone(ZoneOffset.UTC).toLocalDate(),
                to.atZone(ZoneOffset.UTC).toLocalDate());
        return (int) Math.max(0, days);
    }

    private String displayName(UUID userId, Map<UUID, String> names) {
        if (userId == null) {
            return null;
        }
        return names.computeIfAbsent(userId, id ->
                users.findById(id).map(AppUser::getDisplayName).orElse(null));
    }

    private Map<UUID, String> displayNames(WorkflowInstance workflow) {
        Set<UUID> ids = new HashSet<>();
        workflow.getSteps().forEach(s -> {
            if (s.getAssigneeUserId() != null) {
                ids.add(s.getAssigneeUserId());
            }
        });
        workflow.getActions().forEach(a -> {
            if (a.getActorUserId() != null) {
                ids.add(a.getActorUserId());
            }
        });
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
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

    private void requireCurrentReviewer(
            RstPrincipal principal, WorkflowStepAssignment current, RstExercise exercise) {
        if (!ownsStep(principal, current, exercise)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "not-current-reviewer",
                    "This submission is awaiting " + current.getRequiredRoleCode() + " review.");
        }
    }

    private boolean ownsStep(
            RstPrincipal principal, WorkflowStepAssignment current, RstExercise exercise) {
        if (principal == null || current == null) {
            return false;
        }
        Set<String> myPositions = workflowRouter.positionsFor(principal);
        String assigned = resolveStepPosition(current, supervisorPosition(exercise));
        return assigned != null && myPositions.contains(assigned);
    }

    private boolean isAwaitingMyPosition(
            Submission submission,
            WorkflowInstance workflow,
            RstExercise exercise,
            Set<String> myPositions) {
        if (submission == null || !OPEN_STATUSES.contains(submission.getStatus()) || workflow == null) {
            return false;
        }
        String assigned = resolveAssigneePosition(workflow, exercise);
        return assigned != null && myPositions.contains(assigned);
    }

    private WorkflowAction positionDecision(
            WorkflowInstance workflow, RstExercise exercise, Set<String> myPositions) {
        if (workflow == null || myPositions == null || myPositions.isEmpty()) {
            return null;
        }
        return workflow.getActions().stream()
                .filter(action -> "APPROVE".equals(action.getActionType())
                        || "RETURN".equals(action.getActionType()))
                .filter(action -> {
                    String assigned = positionForAction(workflow, exercise, action);
                    return assigned != null && myPositions.contains(assigned);
                })
                .max(Comparator.comparing(WorkflowAction::getActionAt))
                .orElse(null);
    }

    private String resolveAssigneePosition(WorkflowInstance workflow, RstExercise exercise) {
        return workflow.findCurrentReadyStep()
                .map(step -> resolveStepPosition(step, supervisorPosition(exercise)))
                .orElse(null);
    }

    private String resolveStepPosition(WorkflowStepAssignment step, String supervisorPositionId) {
        if (step == null) {
            return null;
        }
        if (hasText(step.getAssigneePositionId())) {
            return step.getAssigneePositionId();
        }
        return workflowRouter.positionIdOrNull(supervisorPositionId, step.getRequiredRoleCode());
    }

    private String positionForAction(
            WorkflowInstance workflow, RstExercise exercise, WorkflowAction action) {
        return workflow.getSteps().stream()
                .filter(step -> step.getStepNo() == action.getStepNo())
                .findFirst()
                .map(step -> resolveStepPosition(step, supervisorPosition(exercise)))
                .orElseGet(() -> workflowRouter.positionIdOrNull(
                        supervisorPosition(exercise), action.getActorRoleCode()));
    }

    private static String supervisorPosition(RstExercise exercise) {
        if (exercise == null || exercise.getToolkitSnapshot() == null) {
            return null;
        }
        return exercise.getToolkitSnapshot().getSupervisorPositionId();
    }

    private static String decisionLabel(String actionType) {
        if ("APPROVE".equals(actionType)) {
            return "Approved";
        }
        if ("RETURN".equals(actionType)) {
            return "Returned";
        }
        return null;
    }

    private static String reviewStageLabel(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "MANAGER" -> "Manager Review";
            case "CDH" -> "Center Delivery Head Review";
            case "LTH" -> "Local Transformation Head Review";
            default -> role;
        };
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

    /** Approver queue query (tab + field filters). */
    public record QueueQuery(
            String status,
            boolean completed,
            String exerciseCode,
            String toolkitName,
            String pl3Name,
            LocalDate submittedFrom,
            LocalDate submittedTo,
            LocalDate completedFrom,
            LocalDate completedTo,
            String decision) {
    }

    /** Approver queue response: filtered rows, filter options, and awaiting metrics. */
    public record ApprovalQueueView(
            List<ApprovalQueueItem> items,
            QueueMetrics metrics,
            List<String> toolkitNames,
            List<String> pl3Names) {
    }

    /** Unfiltered awaiting-tab metrics. */
    public record QueueMetrics(int awaitingMe, int overdue, int dueWithin2Days, int highRisk) {
    }

    /** Approver queue row. */
    public record ApprovalQueueItem(
            UUID submissionId,
            UUID exerciseId,
            String exerciseCode,
            String center,
            String domain,
            String pl3Name,
            String toolkitName,
            String supervisor,
            BigDecimal deliveryHc,
            BigDecimal rightSizingHc,
            BigDecimal productionSupport,
            BigDecimal capacityCreation,
            String previousStep,
            String previousActor,
            Instant previousStepAt,
            Integer agingDays,
            Instant createdAt,
            Instant submittedAt,
            Instant archivedAt,
            String finalStatus,
            Integer reviewDurationDays,
            String status,
            String myDecision,
            Instant myCompletedAt,
            String completedStep) {
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
            List<ActionView> actions,
            boolean canDecide,
            ApprovalWorkspaceView workspace) {
    }

    /** Submission scope view. */
    public record ScopeView(
            String scopeLevel, String center, String site, String domain, String pl3Code,
            String carrier, String customerCountry) {
    }

    /** Workflow step view. */
    public record StepView(
            short stepNo,
            String requiredRoleCode,
            UUID assigneeUserId,
            String assigneePositionId,
            String assigneeDisplayName,
            String routingStatus) {
    }

    /** Workflow action view. */
    public record ActionView(
            short stepNo,
            String actionType,
            UUID actorUserId,
            String actorRoleCode,
            String actorDisplayName,
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
