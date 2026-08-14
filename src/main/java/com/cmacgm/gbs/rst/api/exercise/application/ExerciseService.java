package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseCalendar;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseCalendarRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
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
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.ApplyTemplatesResult;
import com.cmacgm.gbs.rst.api.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.ScenarioAssumption;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.submission.domain.Submission;
import com.cmacgm.gbs.rst.api.submission.persistence.SubmissionRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowAction;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowStepAssignment;
import com.cmacgm.gbs.rst.api.workflow.persistence.WorkflowInstanceRepository;
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
    private final ExerciseCalendarRepository calendars;
    private final ExerciseInitializationService initialization;
    private final HolidayTemplateService holidayTemplates;
    private final ScenarioRepository scenarios;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final SubmissionRepository submissions;
    private final WorkflowInstanceRepository workflows;
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
            ExerciseCalendarRepository calendars,
            ExerciseInitializationService initialization,
            HolidayTemplateService holidayTemplates,
            ScenarioRepository scenarios,
            ExerciseProductionSupportItemRepository supportItems,
            SubmissionRepository submissions,
            WorkflowInstanceRepository workflows,
            WorkflowRouter workflowRouter,
            Clock clock) {
        this.exercises = exercises;
        this.access = access;
        this.freezeResolver = freezeResolver;
        this.timesheet = timesheet;
        this.syncRuns = syncRuns;
        this.teamSetups = teamSetups;
        this.calendars = calendars;
        this.initialization = initialization;
        this.holidayTemplates = holidayTemplates;
        this.scenarios = scenarios;
        this.supportItems = supportItems;
        this.submissions = submissions;
        this.workflows = workflows;
        this.workflowRouter = workflowRouter;
        this.clock = clock;
    }

    /**
     * Creates an Exercise, freezes Toolkit/KPI snapshots, and seeds Associated Data
     * (archive-first copy + multi-year holiday templates).
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
                request.slotStartDate(),
                request.slotWeeks(),
                request.tmsFrom(),
                request.tmsTo(),
                now);
        freeze.applyTo(exercise, now);
        exercise = exercises.saveAndFlush(exercise);
        teamSetups.save(ExerciseTeamSetup.emptyShell(exerciseId, ownerCcgid, now));
        calendars.save(ExerciseCalendar.emptyShell(exerciseId, ownerCcgid, now));
        List<String> notices = new ArrayList<>(initialization.initialize(exercise, ownerCcgid));
        return new CreateExerciseResult(toResponse(exercise, null), notices);
    }

    /**
     * Lists non-deleted Exercises owned by the Supervisor, applying list filters on the server.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param query tab and field filters
     * @return filtered rows and filter options for the current tab
     */
    @Transactional(readOnly = true)
    public ExerciseListView list(String ownerCcgid, ExerciseListQuery query) {
        List<RstExercise> owned =
                exercises.findByOwnerCcgidAndDeletedAtIsNullOrderByUpdatedAtDescIdAsc(ownerCcgid);
        Set<String> tabStatuses = tabStatuses(query.tab());
        List<RstExercise> inTab = owned.stream()
                .filter(item -> tabStatuses.contains(item.getWorkflowStatus()))
                .toList();
        Map<UUID, ReviewProgress> progress = reviewProgressFor(inTab);
        List<ExerciseResponse> source = inTab.stream()
                .map(item -> toResponse(item, progress.get(item.getId())))
                .toList();
        List<ExerciseResponse> items = source.stream()
                .filter(item -> matches(item, query))
                .toList();
        return new ExerciseListView(
                items,
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
        return toResponse(exercise, reviewProgressFor(List.of(exercise)).get(exercise.getId()));
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
        if (!exercise.canDelete()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-deletable",
                    "Only unsubmitted Exercises can be deleted.");
        }
        exercise.softDelete(ownerCcgid, clock.instant());
        exercises.save(exercise);
    }

    /**
     * Updates sizing / slot / TMS periods on an editable Exercise.
     * When the sizing year changes, re-applies Center holiday templates (CUSTOM rows kept).
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
        if (!exercise.canEdit()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "exercise-not-editable",
                    "Exercise periods can only be changed while In Progress or Returned.");
        }
        short previousYear = (short) YearMonth.from(exercise.getSizingMonth()).getYear();
        short nextYear = (short) YearMonth.parse(request.sizingMonth()).getYear();
        exercise.updatePeriods(
                MonthKeys.parseMonthStart(request.sizingMonth()),
                request.slotStartDate(),
                request.slotWeeks(),
                request.tmsFrom(),
                request.tmsTo(),
                ownerCcgid,
                clock.instant());
        exercises.saveAndFlush(exercise);

        List<String> notices = new ArrayList<>();
        if (previousYear != nextYear) {
            String center = exercise.getToolkitSnapshot() != null
                    ? exercise.getToolkitSnapshot().getCenter()
                    : null;
            ApplyTemplatesResult applied = holidayTemplates.applyPublishedTemplates(
                    exercise.getId(),
                    center,
                    nextYear,
                    ExerciseInitializationService.resolveHolidayYears(exercise),
                    ownerCcgid,
                    true);
            notices.add(
                    "Sizing year changed ("
                            + previousYear
                            + " → "
                            + nextYear
                            + "). Holiday templates were re-applied.");
            notices.addAll(applied.notices());
        }
        initialization.ensureTrainVolumeGrids(exercise, ownerCcgid);
        notices.add("Volume Input grids refreshed for the updated training windows.");
        notices.add(initialization.syncTmsPopulation(exercise, ownerCcgid));
        return new UpdateExercisePeriodsResult(toResponse(exercise, null), notices);
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
        return Set.of("IN_PROGRESS", "RETURNED", "UNDER_REVIEW");
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
        if (hasText(query.reviewStage()) && !query.reviewStage().equals(item.requiredRole())) {
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

    private ExerciseResponse toResponse(RstExercise exercise, ReviewProgress progress) {
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
        BigDecimal rightSizingHc = rightSizingHc(exercise.getOfficialScenarioId());
        BigDecimal productionSupport = productionSupport(exercise.getId());
        BigDecimal capacityCreation = rightSizingHc == null
                ? null
                : SizingMath.capacityCreation(deliveryHc, rightSizingHc, productionSupport);
        Integer agingDays = null;
        if ("UNDER_REVIEW".equals(exercise.getWorkflowStatus())
                || "RETURNED".equals(exercise.getWorkflowStatus())) {
            Instant agingFrom = progress != null && progress.agingFrom() != null
                    ? progress.agingFrom()
                    : exercise.getSubmittedAt();
            agingDays = agingFrom == null ? null : daysBetween(agingFrom, clock.instant());
        }
        Instant archivedAt = archivedAt(exercise);
        return new ExerciseResponse(
                exercise.getId(),
                exercise.getExerciseCode(),
                snapshot.getSourceToolkitId(),
                MonthKeys.formatYearMonth(exercise.getSizingMonth()),
                exercise.getSlotStartDate(),
                exercise.getSlotWeeks(),
                exercise.getTmsFrom(),
                exercise.getTmsTo(),
                exercise.getWorkflowStatus(),
                exercise.getOfficialScenarioId(),
                exercise.getSubmittedAt(),
                exercise.canDelete(),
                exercise.canSubmit(),
                exercise.canEdit(),
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

    private Map<UUID, ReviewProgress> reviewProgressFor(List<RstExercise> items) {
        List<RstExercise> tracked = items.stream()
                .filter(item -> "UNDER_REVIEW".equals(item.getWorkflowStatus())
                        || "RETURNED".equals(item.getWorkflowStatus()))
                .toList();
        if (tracked.isEmpty()) {
            return Map.of();
        }
        List<UUID> trackedIds = tracked.stream().map(RstExercise::getId).toList();
        List<Submission> submissionRows = submissions.findByExerciseIdIn(trackedIds);
        if (submissionRows.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Submission> submissionByExercise = submissionRows.stream()
                .collect(Collectors.toMap(Submission::getExerciseId, Function.identity()));
        Map<UUID, WorkflowInstance> workflowBySubmission = workflows
                .findBySubmissionIdIn(submissionRows.stream().map(Submission::getId).toList())
                .stream()
                .collect(Collectors.toMap(WorkflowInstance::getSubmissionId, Function.identity()));
        Set<String> ccgids = new HashSet<>();
        workflowBySubmission.values().forEach(workflow -> {
            workflow.findCurrentReadyStep()
                    .map(WorkflowStepAssignment::getAssigneeCcgid)
                    .ifPresent(ccgids::add);
            workflow.getActions().forEach(action -> {
                if (action.getActorCcgid() != null) {
                    ccgids.add(action.getActorCcgid());
                }
            });
        });
        Map<String, String> names = resolveDisplayNames(ccgids);
        Map<UUID, ReviewProgress> result = new HashMap<>();
        for (RstExercise exercise : tracked) {
            Submission submission = submissionByExercise.get(exercise.getId());
            if (submission == null) {
                continue;
            }
            WorkflowInstance workflow = workflowBySubmission.get(submission.getId());
            if ("RETURNED".equals(exercise.getWorkflowStatus())) {
                WorkflowAction returned = lastReturn(workflow);
                if (returned == null) {
                    continue;
                }
                result.put(exercise.getId(), new ReviewProgress(
                        returned.getStepNo(),
                        returned.getActorRoleCode(),
                        displayName(names, returned.getActorCcgid()),
                        returned.getComments(),
                        returned.getActionAt()));
                continue;
            }
            WorkflowStepAssignment ready = workflow == null
                    ? null
                    : workflow.findCurrentReadyStep().orElse(null);
            String role = ready != null
                    ? ready.getRequiredRoleCode()
                    : roleForStep(submission.getCurrentStep());
            String supervisorPositionId = exercise.getToolkitSnapshot() == null
                    ? null
                    : exercise.getToolkitSnapshot().getSupervisorPositionId();
            String positionId = ready == null
                    ? null
                    : (ready.getAssigneePositionId() != null && !ready.getAssigneePositionId().isBlank()
                            ? ready.getAssigneePositionId()
                            : workflowRouter.positionIdOrNull(supervisorPositionId, ready.getRequiredRoleCode()));
            String reviewer = ready == null
                    ? null
                    : firstNonBlank(
                            workflowRouter.occupantName(ready.getRequiredRoleCode(), positionId),
                            displayName(names, ready.getAssigneeCcgid()));
            result.put(exercise.getId(), new ReviewProgress(
                    submission.getCurrentStep(),
                    role,
                    reviewer,
                    null,
                    exercise.getSubmittedAt()));
        }
        return result;
    }

    private static WorkflowAction lastReturn(WorkflowInstance workflow) {
        if (workflow == null) {
            return null;
        }
        return workflow.getActions().stream()
                .filter(action -> "RETURN".equals(action.getActionType()))
                .max(Comparator
                        .comparing(WorkflowAction::getActionAt)
                        .thenComparingInt(WorkflowAction::getActionSeq))
                .orElse(null);
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
                // skip incomplete support rows
            }
        }
        return total;
    }

    private static Instant archivedAt(RstExercise exercise) {
        if ("APPROVED".equals(exercise.getWorkflowStatus())) {
            return exercise.getValidatedAt();
        }
        if ("REJECTED".equals(exercise.getWorkflowStatus())) {
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
