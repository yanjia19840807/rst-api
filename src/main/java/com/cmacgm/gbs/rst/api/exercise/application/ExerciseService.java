package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.exercise.api.dto.CommittedResultsStatus;
import com.cmacgm.gbs.rst.api.exercise.api.dto.CreateExerciseRequest;
import com.cmacgm.gbs.rst.api.exercise.api.dto.CreateExerciseResult;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseKpiView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseListQuery;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseListView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseResponse;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseSnapshot;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseSubtaskView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseToolkitView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.UpdateExercisePeriodsRequest;
import com.cmacgm.gbs.rst.api.exercise.api.dto.UpdateExercisePeriodsResult;
import com.cmacgm.gbs.rst.api.exercise.api.dto.UpdateSlotPeriodRequest;
import com.cmacgm.gbs.rst.api.exercise.api.dto.UpdateSlotPeriodResult;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SlotVolumeView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeSlotInput;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.WorkingDaysService;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.ScenarioCommitService;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.exercise.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.exercise.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import com.cmacgm.gbs.rst.api.workflow.domain.ExerciseLifecycle;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessTask;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskActor;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskNode;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowAging;
import com.cmacgm.gbs.rst.api.workflow.persistence.ProcessInstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supervisor Exercise application service: create with empty AD shells, list, detail, soft-delete.
 */
@Service
public class ExerciseService {

    private final RstExerciseRepository exercises;
    private final ExerciseAccess access;
    private final ExerciseFreezeResolver freezeResolver;
    private final TimesheetReadService timesheet;
    private final TimesheetSyncRunRepository syncRuns;
    private final ExerciseTeamSetupRepository teamSetups;
    private final ExerciseInitializationService initialization;
    private final WorkingDaysService workingDaysService;
    private final ScenarioRepository scenarios;
    private final ScenarioCommitService scenarioCommits;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ProcessInstanceRepository workflows;
    private final WorkflowRouter workflowRouter;
    private final Clock clock;

    /**
     * Creates the Exercise service.
     */
    public ExerciseService(
            RstExerciseRepository exercises,
            ExerciseAccess access,
            ExerciseFreezeResolver freezeResolver,
            TimesheetReadService timesheet,
            TimesheetSyncRunRepository syncRuns,
            ExerciseTeamSetupRepository teamSetups,
            ExerciseInitializationService initialization,
            WorkingDaysService workingDaysService,
            ScenarioRepository scenarios,
            ScenarioCommitService scenarioCommits,
            ExerciseProductionSupportItemRepository supportItems,
            ProcessInstanceRepository workflows,
            WorkflowRouter workflowRouter,
            Clock clock) {
        this.exercises = exercises;
        this.access = access;
        this.freezeResolver = freezeResolver;
        this.timesheet = timesheet;
        this.syncRuns = syncRuns;
        this.teamSetups = teamSetups;
        this.initialization = initialization;
        this.workingDaysService = workingDaysService;
        this.scenarios = scenarios;
        this.scenarioCommits = scenarioCommits;
        this.supportItems = supportItems;
        this.workflows = workflows;
        this.workflowRouter = workflowRouter;
        this.clock = clock;
    }

    /**
     * Creates an Exercise, freezes Toolkit/KPI snapshots, and seeds Associated Data
     * (archive-first copy of Team Setup, Support, and holidays).
     * Does not start a workflow instance; that happens on the first Submit.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param request create payload
     * @return created Exercise response with initialization notices
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CreateExerciseResult create(String ownerCcgid, CreateExerciseRequest request) {
        // Resolve under repeatable-read so an ACTIVE switch cannot mix snapshots.
        validatePeriods(request.sizingMonth(), request.tmsFrom(), request.tmsTo());
        ExerciseFreeze freeze = freezeResolver.resolve(ownerCcgid, request.toolkitId());

        Instant now = clock.instant();
        UUID exerciseId = UUID.randomUUID();
        String code = "EX-" + LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + exerciseId.toString().substring(0, 8).toUpperCase(Locale.ROOT);

        RstExercise exercise = RstExercise.create(
                exerciseId,
                code,
                freeze.toolkit().getId(),
                ownerCcgid,
                MonthKeys.parseMonthStart(request.sizingMonth()),
                null,
                null,
                request.tmsFrom(),
                request.tmsTo(),
                now);
        freeze.applyTo(exercise, now);
        exercise = exercises.saveAndFlush(exercise);
        teamSetups.save(ExerciseTeamSetup.emptyShell(exerciseId, ownerCcgid, now));
        List<String> notices = new ArrayList<>(initialization.initialize(exercise, ownerCcgid));
        // initialize() may clear the persistence context via bulk @Modifying ops.
        RstExercise reloaded = access.requireOwned(ownerCcgid, exerciseId);
        return new CreateExerciseResult(toResponse(reloaded, null, null), notices);
    }

    /**
     * Lists non-deleted Exercises owned by the Supervisor, applying list filters on the server.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param query tab and field filters
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of filtered rows and filter options for the current tab
     */
    @Transactional(readOnly = true)
    public ExerciseListView list(String ownerCcgid, ExerciseListQuery query, int page, int pageSize) {
        List<RstExercise> owned =
                exercises.findByOwnerCcgidAndDeletedAtIsNullOrderByUpdatedAtDescIdAsc(ownerCcgid);
        Map<UUID, ProcessInstance> processes = processesOf(owned);
        Set<String> tabStatuses = tabStatuses(query.tab());
        List<RstExercise> inTab = owned.stream()
                .filter(item -> tabStatuses.contains(
                        ExerciseLifecycle.workflowStatus(processes.get(item.getId()))))
                .toList();
        Map<UUID, ReviewProgress> progress = reviewProgressFor(inTab, processes);
        List<ExerciseResponse> source = inTab.stream()
                .map(item -> toResponse(
                        item, progress.get(item.getId()), processes.get(item.getId())))
                .toList();
        List<ExerciseResponse> items = source.stream()
                .filter(item -> matches(item, query))
                .toList();
        PageResponse<ExerciseResponse> paged = PageResponse.ofList(items, page, pageSize);
        return new ExerciseListView(
                paged.items(),
                paged.page(),
                paged.pageSize(),
                paged.total(),
                paged.totalPages(),
                distinctNames(source, item -> item.snapshot().toolkit().name()),
                distinctNames(source, item -> item.snapshot().toolkit().pl3Name()),
                distinctNames(source, ExerciseResponse::currentReviewer));
    }

    /**
     * Returns Exercise detail including Official/submit flags.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return Exercise response
     */
    @Transactional(readOnly = true)
    public ExerciseResponse detail(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = access.requireReadable(ownerCcgid, exerciseId);
        ProcessInstance process = processOf(exercise.getId());
        Map<UUID, ProcessInstance> processes = new HashMap<>();
        if (process != null) {
            processes.put(exercise.getId(), process);
        }
        return toResponse(
                exercise,
                reviewProgressFor(List.of(exercise), processes).get(exercise.getId()),
                process);
    }

    /**
     * Soft-deletes an unsubmitted Exercise.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     */
    @Transactional
    public void softDelete(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = access.requireOwned(ownerCcgid, exerciseId);
        if (!ExerciseLifecycle.canDelete(processOf(exercise.getId()))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-deletable",
                    "Only unsubmitted Exercises can be deleted.");
        }
        exercise.softDelete(ownerCcgid, clock.instant());
        exercises.save(exercise);
    }

    /**
     * Updates sizing / TMS periods on an editable Exercise.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param request period payload
     * @return updated Exercise and notices
     */
    @Transactional
    public UpdateExercisePeriodsResult updatePeriods(
            String ownerCcgid, UUID exerciseId, UpdateExercisePeriodsRequest request) {
        validatePeriods(request.sizingMonth(), request.tmsFrom(), request.tmsTo());
        RstExercise exercise = access.requireOwned(ownerCcgid, exerciseId);
        if (!ExerciseLifecycle.canEdit(processOf(exercise.getId()))) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "exercise-not-editable",
                    "Exercise periods can only be changed during Supervisor Sizing.");
        }
        boolean periodsChanged = periodsChanged(exercise, request);
        exercise.updatePeriods(
                MonthKeys.parseMonthStart(request.sizingMonth()),
                request.tmsFrom(),
                request.tmsTo(),
                ownerCcgid,
                clock.instant());
        exercises.saveAndFlush(exercise);

        List<String> notices = new ArrayList<>();
        initialization.ensureTrainVolumeGrids(exercise, ownerCcgid);
        notices.add("Volume Input grids refreshed for the updated training windows.");
        notices.add(initialization.syncTmsPopulation(exercise, ownerCcgid));
        if (periodsChanged) {
            int cleared = scenarioCommits.clearResultsForExercise(exerciseId);
            if (cleared > 0) {
                notices.add(
                        "Cleared saved Forecast and Simulation results for "
                                + cleared
                                + " scenario(s). Re-run Preview / Save sizing on each scenario.");
            }
        }
        // Volume / TMS / Cycle-Time sync may run bulk @Modifying deletes that clear the
        // persistence context; reload before mapping lazy Subtasks / Shared KPI lines.
        RstExercise reloaded = access.requireOwned(ownerCcgid, exerciseId);
        return new UpdateExercisePeriodsResult(
                toResponse(reloaded, null, processOf(reloaded.getId())), notices);
    }

    /**
     * Sets Slot Period, rebuilds an empty Per-slot grid, and clears Slot Simulation only.
     */
    @Transactional
    public UpdateSlotPeriodResult updateSlotPeriod(
            String ownerCcgid, UUID exerciseId, UpdateSlotPeriodRequest request) {
        RstExercise exercise = access.requireOwned(ownerCcgid, exerciseId);
        if (!ExerciseLifecycle.canEdit(processOf(exercise.getId()))) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "exercise-not-editable",
                    "Slot Period can only be changed during Supervisor Sizing.");
        }
        exercise.updateSlotPeriod(
                request.slotStartDate(), request.slotWeeks(), ownerCcgid, clock.instant());
        exercises.saveAndFlush(exercise);
        List<ExerciseVolumeSlotInput> rows = initialization.replaceEmptySlotGrid(exercise, ownerCcgid);
        int cleared = scenarioCommits.clearSlotResultsForExercise(exerciseId);
        List<String> notices = new ArrayList<>();
        notices.add("Per-slot Volume grid generated for the selected Slot Period.");
        if (cleared > 0) {
            notices.add(
                    "Cleared saved Slot Simulation results for "
                            + cleared
                            + " scenario(s).");
        }
        RstExercise reloaded = access.requireOwned(ownerCcgid, exerciseId);
        List<SlotVolumeView> volumes = rows.stream()
                .map(row -> new SlotVolumeView(
                        row.getId(),
                        row.getSlotStartAt(),
                        row.getSlotEndAt(),
                        row.getActualVolume(),
                        row.getSourceType(),
                        row.getImportBatchId()))
                .toList();
        return new UpdateSlotPeriodResult(
                toResponse(reloaded, null, processOf(reloaded.getId())), volumes, notices);
    }

    /**
     * Returns how many scenarios currently have saved Forecast / Simulation snapshots.
     */
    @Transactional(readOnly = true)
    public CommittedResultsStatus committedResults(String ownerCcgid, UUID exerciseId) {
        access.requireOwned(ownerCcgid, exerciseId);
        return new CommittedResultsStatus(scenarioCommits.countScenariosWithResults(exerciseId));
    }

    /**
     * Clears saved Forecast / Simulation snapshots for every scenario.
     * Scenario inputs (name, Right Sizing HC, shifts, Official) are kept.
     */
    @Transactional
    public CommittedResultsStatus clearCommittedResults(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = access.requireOwned(ownerCcgid, exerciseId);
        access.requireEditable(exercise);
        return new CommittedResultsStatus(scenarioCommits.clearResultsForExercise(exerciseId));
    }

    private static boolean periodsChanged(RstExercise exercise, UpdateExercisePeriodsRequest request) {
        LocalDate sizing = MonthKeys.parseMonthStart(request.sizingMonth());
        return !sizing.equals(exercise.getSizingMonth())
                || !request.tmsFrom().equals(exercise.getTmsFrom())
                || !request.tmsTo().equals(exercise.getTmsTo());
    }

    private static void validatePeriods(String sizingMonth, LocalDate tmsFrom, LocalDate tmsTo) {
        if (tmsFrom == null || tmsTo == null || tmsTo.isBefore(tmsFrom)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-tms-period",
                    "tmsTo cannot be before tmsFrom.");
        }
        if (sizingMonth == null || !sizingMonth.matches("^[0-9]{4}-(0[1-9]|1[0-2])$")) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-sizing-month",
                    "sizingMonth must be YYYY-MM.");
        }
    }

    private static Set<String> tabStatuses(String tab) {
        if ("ARCHIVED".equalsIgnoreCase(tab)) {
            return Set.of("APPROVED", "REJECTED");
        }
        return Set.of("IN_PROGRESS", "UNDER_REVIEW");
    }

    /**
     * Current-step filter. {@code SUPERVISOR} is display-only (no workflow instance yet,
     * or after Return / Withdraw). Review roles only match an open Under Review step.
     */
    private static boolean matchesReviewStage(ExerciseResponse item, String reviewStage) {
        if ("SUPERVISOR".equals(reviewStage)) {
            return "IN_PROGRESS".equals(item.workflowStatus());
        }
        return "UNDER_REVIEW".equals(item.workflowStatus())
                && reviewStage.equals(item.requiredRole());
    }

    private static boolean matches(ExerciseResponse item, ExerciseListQuery query) {
        if (hasText(query.exerciseCode())) {
            String code = item.exerciseCode() == null ? "" : item.exerciseCode();
            if (!code.toLowerCase(Locale.ROOT)
                    .contains(query.exerciseCode().trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (hasText(query.toolkitName()) && !query.toolkitName().equals(item.snapshot().toolkit().name())) {
            return false;
        }
        if (hasText(query.pl3Name()) && !query.pl3Name().equals(item.snapshot().toolkit().pl3Name())) {
            return false;
        }
        if (hasText(query.workflowStatus()) && !query.workflowStatus().equals(item.workflowStatus())) {
            return false;
        }
        if (hasText(query.reviewStage()) && !matchesReviewStage(item, query.reviewStage())) {
            return false;
        }
        if (hasText(query.handler()) && !query.handler().equals(item.currentReviewer())) {
            return false;
        }
        if ("ASSIGNED".equalsIgnoreCase(query.officialScenario()) && item.officialScenarioId() == null) {
            return false;
        }
        if ("UNASSIGNED".equalsIgnoreCase(query.officialScenario()) && item.officialScenarioId() != null) {
            return false;
        }
        LocalDate created = dateOf(item.createdAt());
        if (query.createdFrom() != null
                && (created == null || created.isBefore(query.createdFrom()))) {
            return false;
        }
        if (query.createdTo() != null
                && (created == null || created.isAfter(query.createdTo()))) {
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
        LocalDate archived = dateOf(item.archivedAt());
        if (query.archivedFrom() != null
                && (archived == null || archived.isBefore(query.archivedFrom()))) {
            return false;
        }
        if (query.archivedTo() != null
                && (archived == null || archived.isAfter(query.archivedTo()))) {
            return false;
        }
        return true;
    }

    private static List<String> distinctNames(
            List<ExerciseResponse> items, Function<ExerciseResponse, String> getter) {
        return items.stream()
                .map(getter)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
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

    private ExerciseResponse toResponse(
            RstExercise exercise, ReviewProgress progress, ProcessInstance process) {
        ExerciseToolkitSnapshot snapshot = exercise.getToolkitSnapshot();
        if (snapshot == null) {
            throw notFound("exercise-not-found", "The Exercise was not found.");
        }
        LocalDate syncDate = syncRuns.findById(snapshot.getTimesheetSyncRunId())
                .map(run -> run.getSyncDate())
                .orElseThrow(() -> notFound("exercise-not-found", "The Exercise was not found."));
        var toolkitView = new ExerciseToolkitView(
                snapshot.getSourceToolkitId(),
                snapshot.getToolkitName(),
                snapshot.getCenter(),
                snapshot.getDomain(),
                snapshot.getPl1(),
                snapshot.getPl2(),
                snapshot.getPl3Code(),
                snapshot.getPl3Name(),
                snapshot.isCombineSubtasksTime(),
                snapshot.getSourceToolkitVersion());
        var subtasks = exercise.getSubtasks().stream()
                .map(item -> new ExerciseSubtaskView(
                        item.getId(),
                        item.getSourceToolkitSubtaskId(),
                        item.getName(),
                        item.getDescription(),
                        item.getDisplayOrder(),
                        null))
                .toList();
        var kpis = exercise.getSharedKpiLines().stream()
                .map(item -> new ExerciseKpiView(
                        item.getId(),
                        item.getToolkitSharedKpiSelectionId(),
                        item.getCarrier(),
                        item.getSite(),
                        item.getCustomerCountry(),
                        item.getDeliveryHc(),
                        true))
                .toList();
        BigDecimal deliveryHc = deliveryHc(exercise);
        BigDecimal actualHc = actualHeadcount(exercise, deliveryHc);
        BigDecimal rightSizingHc = rightSizingHc(exercise.getOfficialScenarioId());
        BigDecimal productionSupport = productionSupport(exercise.getId());
        BigDecimal capacityCreation = rightSizingHc == null
                ? null
                : SizingMath.capacityCreation(actualHc, rightSizingHc, productionSupport);
        Integer agingDays = null;
        if (ExerciseLifecycle.isUnderReview(process)) {
            Instant agingFrom = progress != null && progress.agingFrom() != null
                    ? progress.agingFrom()
                    : exercise.getSubmittedAt();
            agingDays = agingFrom == null ? null : daysBetween(agingFrom, clock.instant());
        }
        Instant archivedAt = archivedAt(exercise, process);
        return new ExerciseResponse(
                exercise.getId(),
                exercise.getExerciseCode(),
                snapshot.getSourceToolkitId(),
                MonthKeys.formatYearMonth(exercise.getSizingMonth()),
                exercise.getSlotStartDate(),
                exercise.getSlotWeeks(),
                exercise.getTmsFrom(),
                exercise.getTmsTo(),
                ExerciseLifecycle.workflowStatus(process),
                ExerciseLifecycle.submissionStatus(process),
                exercise.getOfficialScenarioId(),
                exercise.getSubmittedAt(),
                ExerciseLifecycle.canDelete(process),
                ExerciseLifecycle.canSubmit(exercise.hasOfficialScenario(), process),
                ExerciseLifecycle.canEdit(process),
                exercise.getVersion(),
                exercise.getCreatedAt(),
                progress == null ? null : progress.currentStep(),
                progress == null ? null : progress.requiredRole(),
                progress == null ? null : progress.currentReviewer(),
                progress == null ? null : progress.lastDecisionComment(),
                deliveryHc,
                rightSizingHc,
                productionSupport,
                capacityCreation,
                agingDays,
                archivedAt,
                new ExerciseSnapshot(toolkitView, subtasks, kpis, syncDate));
    }

    private ProcessInstance processOf(UUID exerciseId) {
        return workflows.findByExerciseId(exerciseId).orElse(null);
    }

    private Map<UUID, ProcessInstance> processesOf(List<RstExercise> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        return workflows.findByExerciseIdIn(items.stream().map(RstExercise::getId).toList())
                .stream()
                .collect(Collectors.toMap(ProcessInstance::getExerciseId, Function.identity()));
    }

    private Map<UUID, ReviewProgress> reviewProgressFor(
            List<RstExercise> items, Map<UUID, ProcessInstance> processes) {
        List<RstExercise> tracked = items.stream()
                .filter(item -> processes.get(item.getId()) != null || item.getSubmittedAt() != null)
                .toList();
        if (tracked.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProcessInstance> workflowByExercise = new HashMap<>();
        for (RstExercise exercise : tracked) {
            ProcessInstance workflow = processes.get(exercise.getId());
            if (workflow != null) {
                workflowByExercise.put(exercise.getId(), workflow);
            }
        }
        if (workflowByExercise.isEmpty()) {
            return Map.of();
        }
        Set<String> ccgids = new HashSet<>();
        workflowByExercise.values().forEach(workflow -> {
            workflow.findCurrentPendingTask()
                    .flatMap(ProcessTask::findAnyPendingActor)
                    .map(TaskActor::getCcgid)
                    .ifPresent(ccgids::add);
            workflow.getTasks().forEach(task -> task.getActors().forEach(actor -> {
                if (actor.getCcgid() != null) {
                    ccgids.add(actor.getCcgid());
                }
            }));
        });
        Map<String, String> names = resolveDisplayNames(ccgids);
        Map<UUID, ReviewProgress> result = new HashMap<>();
        for (RstExercise exercise : tracked) {
            ProcessInstance workflow = workflowByExercise.get(exercise.getId());
            if (workflow == null) {
                continue;
            }
            if ("RETURNED".equals(workflow.submissionStatus())) {
                TaskActor returned = WorkflowAging.lastReturn(workflow);
                if (returned == null) {
                    continue;
                }
                result.put(exercise.getId(), new ReviewProgress(
                        null,
                        null,
                        null,
                        returned.getComments(),
                        null));
                continue;
            }
            if (!workflow.isAwaitingReview()) {
                continue;
            }
            ProcessTask ready = workflow.findCurrentPendingTask().orElse(null);
            TaskActor pending = ready == null ? null : ready.findAnyPendingActor().orElse(null);
            String role = ready != null
                    ? ready.getNode().roleCode()
                    : roleForStep(workflow.getCurrentStep());
            String positionId = pending == null ? null : pending.getPositionId();
            String reviewer = ready == null || pending == null
                    ? null
                    : firstNonBlank(
                            workflowRouter.occupantName(ready.getNode().roleCode(), positionId),
                            displayName(names, pending.getCcgid()));
            Instant agingFrom = WorkflowAging.currentStepStartedAt(workflow, exercise.getSubmittedAt());
            result.put(exercise.getId(), new ReviewProgress(
                    workflow.getCurrentStep(),
                    role,
                    reviewer,
                    null,
                    agingFrom));
        }
        return result;
    }

    private Map<String, String> resolveDisplayNames(Set<String> ccgids) {
        if (ccgids == null || ccgids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        for (String ccgid : ccgids) {
            if (ccgid == null || ccgid.isBlank()) {
                continue;
            }
            names.put(ccgid, timesheet.displayNameByCcgid(ccgid));
        }
        return names;
    }

    private static String displayName(Map<String, String> names, String ccgid) {
        if (ccgid == null) {
            return null;
        }
        return names.get(ccgid);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String roleForStep(Short step) {
        TaskNode node = TaskNode.reviewOf(step);
        return node == null ? null : node.roleCode();
    }

    private record ReviewProgress(
            Short currentStep,
            String requiredRole,
            String currentReviewer,
            String lastDecisionComment,
            Instant agingFrom) {
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

    private static Instant archivedAt(RstExercise exercise, ProcessInstance process) {
        if (ExerciseLifecycle.isApproved(process)) {
            return exercise.getValidatedAt();
        }
        if (ExerciseLifecycle.isRejected(process)) {
            return exercise.getUpdatedAt();
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

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
