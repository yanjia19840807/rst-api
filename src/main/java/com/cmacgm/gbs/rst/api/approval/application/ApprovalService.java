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

import com.cmacgm.gbs.rst.api.associateddata.application.ToolkitVolumeService;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.workingdays.application.WorkingDaysService;
import com.cmacgm.gbs.rst.api.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowViews;
import com.cmacgm.gbs.rst.api.workflow.domain.ActorStatus;
import com.cmacgm.gbs.rst.api.workflow.domain.ActorType;
import com.cmacgm.gbs.rst.api.workflow.domain.ExerciseLifecycle;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessStatus;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessTask;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskActor;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskNode;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskStatus;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowAging;
import com.cmacgm.gbs.rst.api.workflow.persistence.ProcessInstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cmacgm.gbs.rst.api.approval.api.dto.ActionView;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalDetailView;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalQueueItem;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalQueueView;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApprovalWorkspaceView;
import com.cmacgm.gbs.rst.api.approval.api.dto.ApproveRequest;
import com.cmacgm.gbs.rst.api.approval.api.dto.QueueMetrics;
import com.cmacgm.gbs.rst.api.approval.api.dto.QueueQuery;
import com.cmacgm.gbs.rst.api.approval.api.dto.ReturnRequest;
import com.cmacgm.gbs.rst.api.approval.api.dto.ScopeView;
import com.cmacgm.gbs.rst.api.approval.api.dto.StepView;

/**
 * Approver queue, review detail, Approve/Return, and Supervisor Withdraw.
 */
@Service
public class ApprovalService {

    private static final Set<ProcessStatus> OPEN_STATUSES = Set.of(ProcessStatus.OPEN);
    private static final Set<ProcessStatus> ARCHIVED_STATUSES = Set.of(ProcessStatus.FINISHED);
    private static final Set<ProcessStatus> QUEUE_STATUSES = Set.of(
            ProcessStatus.OPEN, ProcessStatus.FINISHED);

    private final ProcessInstanceRepository workflows;
    private final RstExerciseRepository exercises;
    private final ScenarioRepository scenarios;
    private final ExerciseTeamSetupRepository teamSetups;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final WorkingDaysService workingDaysService;
    private final TimesheetReadService timesheet;
    private final WorkflowRouter workflowRouter;
    private final WorkflowViews workflowViews;
    private final ApprovalWorkspaceAssembler workspaceAssembler;
    private final ToolkitVolumeService toolkitVolumes;
    private final Clock clock;

    /**
     * Creates the Approval service.
     */
    public ApprovalService(
            ProcessInstanceRepository workflows,
            RstExerciseRepository exercises,
            ScenarioRepository scenarios,
            ExerciseTeamSetupRepository teamSetups,
            ExerciseProductionSupportItemRepository supportItems,
            WorkingDaysService workingDaysService,
            TimesheetReadService timesheet,
            WorkflowRouter workflowRouter,
            WorkflowViews workflowViews,
            ApprovalWorkspaceAssembler workspaceAssembler,
            ToolkitVolumeService toolkitVolumes,
            Clock clock) {
        this.workflows = workflows;
        this.exercises = exercises;
        this.scenarios = scenarios;
        this.teamSetups = teamSetups;
        this.supportItems = supportItems;
        this.workingDaysService = workingDaysService;
        this.timesheet = timesheet;
        this.workflowRouter = workflowRouter;
        this.workflowViews = workflowViews;
        this.workspaceAssembler = workspaceAssembler;
        this.toolkitVolumes = toolkitVolumes;
        this.clock = clock;
    }

    /**
     * Lists submissions for the Approver queue, applying the given filters on the server.
     *
     * <p>Awaiting Review only includes submissions whose current pending actor is assigned
     * to a Timesheet position the caller occupies. Completed Task includes submissions
     * where that position has already Approved or Returned.
     *
     * @param principal current approver
     * @param query tab, status, and field filters
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of filtered rows, filter options, and awaiting-me metrics
     */
    @Transactional(readOnly = true)
    public ApprovalQueueView queue(RstPrincipal principal, QueueQuery query, int page, int pageSize) {
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
        PageResponse<ApprovalQueueItem> paged = PageResponse.ofList(items, page, pageSize);
        return new ApprovalQueueView(
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                toMetrics(awaiting),
                distinctNames(source, ApprovalQueueItem::toolkitName),
                distinctNames(source, ApprovalQueueItem::pl3Name));
    }

    private List<ApprovalQueueItem> listItems(
            Set<ProcessStatus> statuses, Set<String> myPositions, boolean awaitingOnly, boolean byAging) {
        Map<String, String> names = new HashMap<>();
        List<ApprovalQueueItem> items = new ArrayList<>();
        for (ProcessInstance workflow : workflows.findByStatusInOrderBySubmittedAtDesc(statuses)) {
            RstExercise exercise = exercises.findById(workflow.getExerciseId()).orElse(null);
            if (exercise == null || exercise.getDeletedAt() != null) {
                continue;
            }
            boolean awaitingMe = isAwaitingMyPosition(workflow, myPositions);
            TaskActor mine = positionDecision(workflow, myPositions);
            if (awaitingOnly && !awaitingMe) {
                continue;
            }
            if (!awaitingOnly && (mine == null || awaitingMe)) {
                continue;
            }
            items.add(toQueueItem(workflow, exercise, names, mine));
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
     * <p>Steps 1–2 advance the READY hop; status stays OPEN. Step 3 marks
     * the workflow and Exercise APPROVED.
     *
     * @param principal acting approver
     * @param submissionId submission id
     * @param request approve payload
     * @return updated review detail
     */
    @Transactional
    public ApprovalDetailView approve(RstPrincipal principal, UUID submissionId, ApproveRequest request) {
        Loaded loaded = load(submissionId);
        if (!loaded.workflow().isOpen()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "submission-not-awaiting",
                    "Submission is not awaiting approval.");
        }
        UUID requestId = request.requestId() == null ? UUID.randomUUID() : request.requestId();
        if (loaded.workflow().findActorByRequestId(requestId).isPresent()) {
            return toDetail(loaded, principal);
        }

        ProcessTask current = loaded.workflow().findCurrentPendingTask()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "workflow-step-not-ready",
                        "Current workflow step is not READY."));
        TaskActor actor = requirePendingActor(principal, current);
        Instant now = clock.instant();
        loaded.workflow().approve(actor, request.comments(), requestId, now);

        if (current.getStatus() == TaskStatus.APPROVED) {
            TaskNode next = current.getNode().nextReview();
            if (next == TaskNode.CDH) {
                WorkflowRouter.RoutedStep cdh = workflowRouter.resolveCdh(
                        toolkitCenter(loaded.exercise()), toolkitDomain(loaded.exercise()));
                loaded.workflow().openReview(
                        next,
                        List.of(new ProcessInstance.Assignee(cdh.positionId(), cdh.assigneeCcgid())),
                        now);
            } else if (next == TaskNode.LTH) {
                WorkflowRouter.RoutedStep lth = workflowRouter.resolveLth();
                loaded.workflow().openReview(
                        next,
                        List.of(new ProcessInstance.Assignee(lth.positionId(), lth.assigneeCcgid())),
                        now);
            } else if (current.getNode() == TaskNode.LTH) {
                toolkitVolumes.freezeOfficialTrainingAndUpsert(
                        loaded.exercise(), principal.ccgid(), now);
                loaded.exercise().markApproved(principal.ccgid(), now);
            }
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
        if (!loaded.workflow().isOpen()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "submission-not-awaiting",
                    "Submission is not awaiting approval.");
        }
        UUID requestId = request.requestId() == null ? UUID.randomUUID() : request.requestId();
        if (loaded.workflow().findActorByRequestId(requestId).isPresent()) {
            return toDetail(loaded, principal);
        }

        ProcessTask current = loaded.workflow().findCurrentPendingTask()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "workflow-step-not-ready",
                        "Current workflow step is not READY."));
        TaskActor actor = requirePendingActor(principal, current);
        Instant now = clock.instant();
        loaded.workflow().returnToSupervisor(actor, request.comments(), requestId, now);
        reopenExercise(loaded, principal.ccgid(), now, true);
        persist(loaded);
        return toDetail(loaded, principal);
    }

    /**
     * Rejects the submission and ends the process. The Exercise is not reopened.
     *
     * @param principal acting approver
     * @param submissionId submission id
     * @param request reject payload (comments required)
     * @return updated review detail
     */
    @Transactional
    public ApprovalDetailView reject(RstPrincipal principal, UUID submissionId, ReturnRequest request) {
        if (request.comments() == null || request.comments().isBlank()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "comments-required",
                    "Reject comments are required.");
        }
        Loaded loaded = load(submissionId);
        if (!loaded.workflow().isOpen()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "submission-not-awaiting",
                    "Submission is not awaiting approval.");
        }
        UUID requestId = request.requestId() == null ? UUID.randomUUID() : request.requestId();
        if (loaded.workflow().findActorByRequestId(requestId).isPresent()) {
            return toDetail(loaded, principal);
        }

        ProcessTask current = loaded.workflow().findCurrentPendingTask()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "workflow-step-not-ready",
                        "Current workflow step is not READY."));
        TaskActor actor = requirePendingActor(principal, current);
        Instant now = clock.instant();
        loaded.workflow().refuse(actor, request.comments(), requestId, now);
        loaded.exercise().markRejected(principal.ccgid(), now);
        persist(loaded);
        return toDetail(loaded, principal);
    }

    /**
     * Withdraws an UNDER_REVIEW submission as Supervisor: cancels workflow and reopens
     * the Exercise for editing. Official stays on the Exercise pointer.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return review detail after withdraw
     */
    @Transactional
    public ApprovalDetailView withdraw(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.findByIdAndOwnerCcgidAndDeletedAtIsNull(exerciseId, ownerCcgid)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "exercise-not-found", "The Exercise was not found."));
        ProcessInstance workflow = workflows.findByExerciseId(exerciseId).orElse(null);
        if (workflow == null || !ExerciseLifecycle.canWithdraw(workflow)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-withdrawable",
                    "Only UNDER_REVIEW Exercises can be withdrawn.");
        }

        Instant now = clock.instant();
        workflow.withdraw(ownerCcgid, UUID.randomUUID(), now);
        Loaded loaded = new Loaded(workflow, exercise);
        reopenExercise(loaded, ownerCcgid, now, false);
        persist(loaded);
        return toDetail(loaded, null);
    }

    /**
     * Reopens the Exercise after Return or Withdraw.
     * Keeps the Official Scenario pointer and status; Official is only a flag.
     * Associated Data and scenario content are not rewritten.
     */
    private void reopenExercise(Loaded loaded, String actorCcgid, Instant now, boolean returned) {
        if (returned) {
            loaded.exercise().markReturned(actorCcgid, now);
        } else {
            loaded.exercise().markWithdrawn(actorCcgid, now);
        }
    }

    private Loaded load(UUID submissionId) {
        ProcessInstance workflow = workflows.findById(submissionId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "submission-not-found", "The Submission was not found."));
        RstExercise exercise = exercises.findById(workflow.getExerciseId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "exercise-not-found", "The Exercise was not found."));
        return new Loaded(workflow, exercise);
    }

    private void persist(Loaded loaded) {
        workflows.save(loaded.workflow());
        exercises.save(loaded.exercise());
    }

    private ApprovalDetailView toDetail(Loaded loaded, RstPrincipal principal) {
        UUID scenarioId = loaded.exercise().getOfficialScenarioId();
        String scenarioName = scenarioId == null
                ? null
                : scenarios.findById(scenarioId).map(Scenario::getName).orElse(null);
        Map<String, String> displayNames = displayNames(loaded.workflow());
        List<ScopeView> scopes = loaded.workflow().getScopes().stream()
                .map(s -> new ScopeView(
                        s.getScopeLevel(), s.getCenter(), s.getSite(), s.getDomain(),
                        s.getPl3Code(), s.getCarrier(), s.getCustomerCountry()))
                .toList();
        List<StepView> steps = workflowViews.steps(loaded.workflow(), displayNames);
        List<ActionView> actions = workflowViews.actions(loaded.workflow(), displayNames);
        String requiredRole = loaded.workflow().findCurrentPendingTask()
                .map(task -> task.getNode().roleCode())
                .orElseGet(() -> roleForStep(loaded.workflow().getCurrentStep()));
        boolean canDecide = loaded.workflow().isOpen()
                && loaded.workflow().findCurrentPendingTask()
                        .flatMap(task -> task.findPendingActor(workflowRouter.positionsFor(principal)))
                        .isPresent();
        ApprovalWorkspaceView workspace = canDecide
                ? workspaceAssembler.inProgress(
                        loaded.workflow(),
                        loaded.exercise(),
                        displayNames)
                : workspaceAssembler.completed(
                        loaded.workflow(),
                        loaded.exercise(),
                        principal,
                        displayNames);
        return new ApprovalDetailView(
                loaded.exercise().getId(),
                loaded.exercise().getExerciseCode(),
                ExerciseLifecycle.workflowStatus(loaded.workflow()),
                loaded.exercise().getSubmittedAt(),
                scenarioId,
                scenarioName,
                loaded.workflow().getId(),
                loaded.workflow().submissionStatus(),
                loaded.workflow().getCurrentStep(),
                requiredRole,
                loaded.workflow().getRemarks(),
                scopes,
                steps,
                actions,
                canDecide,
                workspace);
    }

    private ApprovalQueueItem toQueueItem(
            ProcessInstance workflow,
            RstExercise exercise,
            Map<String, String> names,
            TaskActor mine) {
        var snapshot = exercise.getToolkitSnapshot();
        String toolkitName = snapshot != null ? snapshot.getToolkitName() : "";
        String pl3Name = snapshot != null ? snapshot.getPl3Name() : "";
        String center = snapshot != null ? snapshot.getCenter() : "";
        String domain = snapshot != null ? snapshot.getDomain() : "";
        String supervisor = displayName(exercise.getOwnerCcgid(), names);

        BigDecimal deliveryHc = deliveryHc(exercise);
        UUID scenarioId = exercise.getOfficialScenarioId();
        BigDecimal rightSizingHc = rightSizingHc(scenarioId);
        BigDecimal productionSupport = productionSupport(exercise.getId());
        BigDecimal capacityCreation = rightSizingHc == null
                ? null
                : SizingMath.capacityCreation(
                        actualHeadcount(exercise, deliveryHc), rightSizingHc, productionSupport);

        TaskActor previous = WorkflowAging.lastApprove(workflow);
        TaskActor last = lastCompletedActor(workflow);
        String previousStep = previous == null || previous.getTask() == null
                ? null
                : reviewStageLabel(previous.getTask().getNode().roleCode());
        String previousActor = last != null
                ? displayName(last.getCcgid(), names)
                : supervisor;
        Instant previousStepAt = last != null ? last.getActedAt() : workflow.getSubmittedAt();
        Instant agingFrom = WorkflowAging.currentStepStartedAt(workflow, workflow.getSubmittedAt());
        int agingDays = daysBetween(agingFrom, clock.instant());

        Instant archivedAt = archivedAt(exercise, workflow);
        Integer reviewDurationDays = ARCHIVED_STATUSES.contains(workflow.getStatus())
                ? daysBetween(workflow.getSubmittedAt(), archivedAt)
                : null;
        String myDecision = mine == null
                ? null
                : decisionLabel(mine.getStatus());
        Instant myCompletedAt = mine == null ? null : mine.getActedAt();
        String completedStep = mine == null || mine.getTask() == null
                ? null
                : reviewStageLabel(mine.getTask().getNode().roleCode());

        return new ApprovalQueueItem(
                workflow.getId(),
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
                workflow.getSubmittedAt(),
                archivedAt,
                finalStatus(workflow.submissionStatus()),
                reviewDurationDays,
                workflow.submissionStatus(),
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

    private BigDecimal actualHeadcount(RstExercise exercise, BigDecimal deliveryHc) {
        ExerciseTeamSetup setup = teamSetups.findById(exercise.getId()).orElse(null);
        return SizingMath.actualHeadcount(setup == null ? null : setup.totalAgents(), deliveryHc);
    }

    private BigDecimal rightSizingHc(UUID scenarioId) {
        if (scenarioId == null) {
            return null;
        }
        Scenario scenario = scenarios.findById(scenarioId).orElse(null);
        if (scenario == null) {
            return null;
        }
        return SizingMath.measuredRightSizingHc(scenario.getRightSizingHc());
    }

    private BigDecimal productionSupport(UUID exerciseId) {
        return SupportWorkloadMath.totalSupportFte(
                supportItems.findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(exerciseId),
                teamSetups.findById(exerciseId).orElse(null),
                workingDaysService.workingDaysPerYear(exerciseId));
    }

    private static TaskActor lastCompletedActor(ProcessInstance workflow) {
        if (workflow == null) {
            return null;
        }
        return workflow.getTasks().stream()
                .flatMap(task -> task.getActors().stream())
                .filter(actor -> actor.getStatus() == ActorStatus.APPROVED)
                .max(Comparator.comparing(TaskActor::getActedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static Instant archivedAt(RstExercise exercise, ProcessInstance workflow) {
        if (exercise.getValidatedAt() != null) {
            return exercise.getValidatedAt();
        }
        if (workflow != null) {
            Instant fromAction = workflow.getTasks().stream()
                    .flatMap(task -> task.getActors().stream())
                    .filter(actor -> actor.getStatus() == ActorStatus.RETURNED
                            || actor.getStatus() == ActorStatus.REJECTED
                            || actor.getStatus() == ActorStatus.WITHDRAWN)
                    .map(TaskActor::getActedAt)
                    .max(Instant::compareTo)
                    .orElse(exercise.getUpdatedAt());
            return fromAction != null ? fromAction : workflow.getSubmittedAt();
        }
        return exercise.getUpdatedAt();
    }

    private static String finalStatus(String submissionStatus) {
        if ("APPROVED".equals(submissionStatus)) {
            return "Approved";
        }
        if ("RETURNED".equals(submissionStatus)
                || "WITHDRAWN".equals(submissionStatus)
                || "REJECTED".equals(submissionStatus)) {
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

    private String displayName(String ccgid, Map<String, String> names) {
        if (ccgid == null) {
            return null;
        }
        return names.computeIfAbsent(ccgid, timesheet::displayNameByCcgid);
    }

    private Map<String, String> displayNames(ProcessInstance workflow) {
        Set<String> ids = new HashSet<>();
        workflow.getTasks().forEach(task -> task.getActors().forEach(actor -> {
            if (actor.getCcgid() != null) {
                ids.add(actor.getCcgid());
            }
        }));
        Map<String, String> names = new HashMap<>();
        for (String ccgid : ids) {
            names.put(ccgid, timesheet.displayNameByCcgid(ccgid));
        }
        return names;
    }

    private static Set<ProcessStatus> resolveOpenFilter(String status) {
        if (status == null || status.isBlank() || "AWAITING".equalsIgnoreCase(status)
                || "OPEN".equalsIgnoreCase(status)) {
            return OPEN_STATUSES;
        }
        String normalized = status.trim().toUpperCase();
        if ("OPEN".equals(normalized)) {
            return OPEN_STATUSES;
        }
        if (normalized.startsWith("AWAITING")) {
            return OPEN_STATUSES;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "invalid-status-filter",
                "status must be AWAITING, OPEN, or omitted.");
    }

    private TaskActor requirePendingActor(RstPrincipal principal, ProcessTask current) {
        Set<String> myPositions = workflowRouter.positionsFor(principal);
        return current.findPendingActor(myPositions)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "not-current-reviewer",
                        "This submission is awaiting " + current.getNode().roleCode() + " review."));
    }

    private boolean isAwaitingMyPosition(ProcessInstance workflow, Set<String> myPositions) {
        if (workflow == null || !workflow.isOpen()) {
            return false;
        }
        return workflow.findCurrentPendingTask()
                .flatMap(task -> task.findPendingActor(myPositions))
                .isPresent();
    }

    private TaskActor positionDecision(ProcessInstance workflow, Set<String> myPositions) {
        if (workflow == null || myPositions == null || myPositions.isEmpty()) {
            return null;
        }
        return workflow.getTasks().stream()
                .flatMap(task -> task.getActors().stream())
                .filter(actor -> actor.getActorType() != ActorType.INITIATOR)
                .filter(actor -> actor.getStatus() == ActorStatus.APPROVED
                        || actor.getStatus() == ActorStatus.RETURNED
                        || actor.getStatus() == ActorStatus.REJECTED)
                .filter(actor -> actor.getPositionId() != null && myPositions.contains(actor.getPositionId()))
                .max(Comparator.comparing(TaskActor::getActedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static String toolkitCenter(RstExercise exercise) {
        if (exercise == null || exercise.getToolkitSnapshot() == null) {
            return null;
        }
        return exercise.getToolkitSnapshot().getCenter();
    }

    private static String toolkitDomain(RstExercise exercise) {
        if (exercise == null || exercise.getToolkitSnapshot() == null) {
            return null;
        }
        return exercise.getToolkitSnapshot().getDomain();
    }

    private static String decisionLabel(ActorStatus status) {
        return switch (status) {
            case APPROVED -> "Approved";
            case RETURNED -> "Returned";
            case REJECTED -> "Rejected";
            default -> null;
        };
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

    private record Loaded(ProcessInstance workflow, RstExercise exercise) {
    }
}
