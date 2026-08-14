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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseCalendar;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseCalendarRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.ApplyTemplatesResult;
import com.cmacgm.gbs.rst.api.identity.domain.AppUser;
import com.cmacgm.gbs.rst.api.identity.persistence.AppUserRepository;
import com.cmacgm.gbs.rst.api.official.domain.OfficialPackage;
import com.cmacgm.gbs.rst.api.official.persistence.OfficialPackageRepository;
import com.cmacgm.gbs.rst.api.scenario.application.sizing.SizingMath;
import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import com.cmacgm.gbs.rst.api.scenario.domain.ScenarioAssumption;
import com.cmacgm.gbs.rst.api.scenario.persistence.ScenarioRepository;
import com.cmacgm.gbs.rst.api.submission.domain.Submission;
import com.cmacgm.gbs.rst.api.submission.persistence.SubmissionRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitRepository;
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
    private final ToolkitRepository toolkits;
    private final TimesheetReadService timesheet;
    private final TimesheetSyncRunRepository syncRuns;
    private final ExerciseTeamSetupRepository teamSetups;
    private final ExerciseCalendarRepository calendars;
    private final ExerciseInitializationService initialization;
    private final HolidayTemplateService holidayTemplates;
    private final OfficialPackageRepository officialPackages;
    private final ScenarioRepository scenarios;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final SubmissionRepository submissions;
    private final WorkflowInstanceRepository workflows;
    private final AppUserRepository users;
    private final WorkflowRouter workflowRouter;
    private final Clock clock;

    /**
     * Creates the Exercise service.
     */
    public ExerciseService(
            RstExerciseRepository exercises,
            ToolkitRepository toolkits,
            TimesheetReadService timesheet,
            TimesheetSyncRunRepository syncRuns,
            ExerciseTeamSetupRepository teamSetups,
            ExerciseCalendarRepository calendars,
            ExerciseInitializationService initialization,
            HolidayTemplateService holidayTemplates,
            OfficialPackageRepository officialPackages,
            ScenarioRepository scenarios,
            ExerciseProductionSupportItemRepository supportItems,
            SubmissionRepository submissions,
            WorkflowInstanceRepository workflows,
            AppUserRepository users,
            WorkflowRouter workflowRouter,
            Clock clock) {
        this.exercises = exercises;
        this.toolkits = toolkits;
        this.timesheet = timesheet;
        this.syncRuns = syncRuns;
        this.teamSetups = teamSetups;
        this.calendars = calendars;
        this.initialization = initialization;
        this.holidayTemplates = holidayTemplates;
        this.officialPackages = officialPackages;
        this.scenarios = scenarios;
        this.supportItems = supportItems;
        this.submissions = submissions;
        this.workflows = workflows;
        this.users = users;
        this.workflowRouter = workflowRouter;
        this.clock = clock;
    }

    /**
     * Creates an Exercise, freezes Toolkit/KPI snapshots, and seeds Associated Data
     * (archive-first copy + multi-year holiday templates).
     *
     * @param ownerId Supervisor user id
     * @param ccgid Supervisor CCGID
     * @param request create payload
     * @return created Exercise response with initialization notices
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CreateExerciseResult create(UUID ownerId, String ccgid, CreateExercise request) {
        // Resolve under repeatable-read so an ACTIVE switch cannot mix snapshots.
        validatePeriods(request.sizingMonth(), request.tmsFrom(), request.tmsTo());
        ExerciseFreeze freeze = ExerciseFreeze.resolve(ccgid, request.toolkitId(), toolkits, timesheet);

        Instant now = clock.instant();
        UUID exerciseId = UUID.randomUUID();
        String code = "EX-" + LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + exerciseId.toString().substring(0, 8).toUpperCase(Locale.ROOT);

        RstExercise exercise = RstExercise.create(
                exerciseId,
                code,
                freeze.toolkit().getId(),
                ownerId,
                MonthKeys.parseMonthStart(request.sizingMonth()),
                request.slotStartDate(),
                request.slotWeeks(),
                request.tmsFrom(),
                request.tmsTo(),
                now);
        freeze.applyTo(exercise, now);
        exercise = exercises.saveAndFlush(exercise);
        teamSetups.save(ExerciseTeamSetup.emptyShell(exerciseId, ownerId, now));
        calendars.save(ExerciseCalendar.emptyShell(exerciseId, ownerId, now));
        List<String> notices = new ArrayList<>(initialization.initialize(exercise, ownerId));
        return new CreateExerciseResult(toResponse(exercise, null), notices);
    }

    /**
     * Lists non-deleted Exercises owned by the Supervisor, applying list filters on the server.
     *
     * @param ownerId Supervisor user id
     * @param query tab and field filters
     * @return filtered rows and filter options for the current tab
     */
    @Transactional(readOnly = true)
    public ExerciseListView list(UUID ownerId, ListQuery query) {
        List<RstExercise> owned =
                exercises.findByOwnerUserIdAndDeletedAtIsNullOrderByUpdatedAtDescIdAsc(ownerId);
        Set<String> tabStatuses = tabStatuses(query.tab());
        List<RstExercise> inTab = owned.stream()
                .filter(item -> tabStatuses.contains(item.getWorkflowStatus()))
                .toList();
        Map<UUID, ReviewProgress> progress = reviewProgressFor(inTab);
        List<Exercise> source = inTab.stream()
                .map(item -> toResponse(item, progress.get(item.getId())))
                .toList();
        List<Exercise> items = source.stream()
                .filter(item -> matches(item, query))
                .toList();
        return new ExerciseListView(
                items,
                distinctNames(source, item -> item.snapshot().toolkit().name()),
                distinctNames(source, item -> item.snapshot().toolkit().pl3Name()),
                distinctNames(source, Exercise::currentReviewer));
    }

    /**
     * Returns Exercise detail including Official/submit flags.
     *
     * @param ownerId Supervisor user id
     * @param exerciseId Exercise id
     * @return Exercise response
     */
    @Transactional(readOnly = true)
    public Exercise detail(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = requireReadable(ownerId, exerciseId);
        return toResponse(exercise, reviewProgressFor(List.of(exercise)).get(exercise.getId()));
    }

    /**
     * Soft-deletes an unsubmitted Exercise.
     *
     * @param ownerId Supervisor user id
     * @param exerciseId Exercise id
     */
    @Transactional
    public void softDelete(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = requireOwned(ownerId, exerciseId);
        if (!exercise.canDelete()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-deletable",
                    "Only unsubmitted Exercises can be deleted.");
        }
        exercise.softDelete(ownerId, clock.instant());
        exercises.save(exercise);
    }

    /**
     * Updates sizing / slot / TMS periods on an editable Exercise.
     * When the sizing year changes, re-applies Center holiday templates (CUSTOM rows kept).
     *
     * @param ownerId Supervisor user id
     * @param exerciseId Exercise id
     * @param request period payload
     * @return updated Exercise and notices
     */
    @Transactional
    public UpdateExercisePeriodsResult updatePeriods(
            UUID ownerId, UUID exerciseId, UpdateExercisePeriods request) {
        validatePeriods(request.sizingMonth(), request.tmsFrom(), request.tmsTo());
        RstExercise exercise = requireOwned(ownerId, exerciseId);
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
                ownerId,
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
                    ownerId,
                    true);
            notices.add(
                    "Sizing year changed ("
                            + previousYear
                            + " → "
                            + nextYear
                            + "). Holiday templates were re-applied.");
            notices.addAll(applied.notices());
        }
        initialization.ensureTrainVolumeGrids(exercise, ownerId);
        notices.add("Volume Input grids refreshed for the updated training windows.");
        notices.add(initialization.syncTmsPopulation(exercise, ownerId));
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

    /**
     * Previews the Toolkit/Timesheet freeze without persisting an Exercise.
     *
     * @param ccgid Supervisor CCGID
     * @param request create payload
     * @return preview snapshot
     */
    @Transactional(readOnly = true)
    public ExerciseSnapshot preview(String ccgid, CreateExercise request) {
        validatePeriods(request.sizingMonth(), request.tmsFrom(), request.tmsTo());
        return ExerciseFreeze.resolve(ccgid, request.toolkitId(), toolkits, timesheet).toSnapshot();
    }

    /**
     * Loads a non-deleted Exercise owned by the given Supervisor.
     *
     * @param ownerId Supervisor user id
     * @param exerciseId Exercise id
     * @return Exercise aggregate
     */
    @Transactional(readOnly = true)
    public RstExercise requireOwned(UUID ownerId, UUID exerciseId) {
        return exercises.findByIdAndOwnerUserIdAndDeletedAtIsNull(exerciseId, ownerId)
                .orElseThrow(() -> notFound("exercise-not-found", "The Exercise was not found."));
    }

    /**
     * Loads a non-deleted Exercise the principal may read: the owner, or any user when the
     * Exercise has (or had) a submission — so Approvers can open Submitted Exercise data.
     *
     * @param userId acting principal
     * @param exerciseId Exercise id
     * @return Exercise aggregate
     */
    @Transactional(readOnly = true)
    public RstExercise requireReadable(UUID userId, UUID exerciseId) {
        RstExercise exercise = exercises.findByIdAndDeletedAtIsNull(exerciseId)
                .orElseThrow(() -> notFound("exercise-not-found", "The Exercise was not found."));
        if (userId.equals(exercise.getOwnerUserId())) {
            return exercise;
        }
        List<OfficialPackage> packages = officialPackages.findByExerciseId(exerciseId);
        if (packages.isEmpty()) {
            throw notFound("exercise-not-found", "The Exercise was not found.");
        }
        List<UUID> packageIds = packages.stream().map(OfficialPackage::getId).toList();
        if (submissions.findByOfficialPackageIdIn(packageIds).isEmpty()) {
            throw notFound("exercise-not-found", "The Exercise was not found.");
        }
        return exercise;
    }

    /**
     * Ensures the Exercise is editable (IN_PROGRESS / RETURNED).
     *
     * @param exercise Exercise aggregate
     */
    public void requireEditable(RstExercise exercise) {
        if (!exercise.canEdit()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "exercise-not-editable",
                    "The Exercise is not editable in its current workflow status.");
        }
    }

    private static Set<String> tabStatuses(String tab) {
        if ("ARCHIVED".equalsIgnoreCase(tab)) {
            return Set.of("VALIDATED", "ARCHIVED");
        }
        return Set.of("IN_PROGRESS", "RETURNED", "UNDER_REVIEW");
    }

    private static boolean matches(Exercise item, ListQuery query) {
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

    private static List<String> distinctNames(List<Exercise> items, Function<Exercise, String> getter) {
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

    private Exercise toResponse(RstExercise exercise, ReviewProgress progress) {
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
        return new Exercise(
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
        Map<UUID, OfficialPackage> packageByExercise = currentOrLatestPackage(
                officialPackages.findByExerciseIdIn(trackedIds));
        if (packageByExercise.isEmpty()) {
            return Map.of();
        }
        List<Submission> submissionRows = submissions.findByOfficialPackageIdIn(
                packageByExercise.values().stream().map(OfficialPackage::getId).toList());
        Map<UUID, Submission> submissionByPackage = submissionRows.stream()
                .collect(Collectors.toMap(Submission::getOfficialPackageId, Function.identity()));
        Map<UUID, WorkflowInstance> workflowBySubmission = workflows
                .findBySubmissionIdIn(submissionRows.stream().map(Submission::getId).toList())
                .stream()
                .collect(Collectors.toMap(WorkflowInstance::getSubmissionId, Function.identity()));
        Set<UUID> userIds = new HashSet<>();
        workflowBySubmission.values().forEach(workflow -> {
            workflow.findCurrentReadyStep()
                    .map(WorkflowStepAssignment::getAssigneeUserId)
                    .ifPresent(userIds::add);
            workflow.getActions().forEach(action -> {
                if (action.getActorUserId() != null) {
                    userIds.add(action.getActorUserId());
                }
            });
        });
        Map<UUID, String> names = userIds.isEmpty()
                ? Map.of()
                : users.findAllById(userIds).stream()
                        .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        Map<UUID, ReviewProgress> result = new HashMap<>();
        for (RstExercise exercise : tracked) {
            OfficialPackage pkg = packageByExercise.get(exercise.getId());
            if (pkg == null) {
                continue;
            }
            Submission submission = submissionByPackage.get(pkg.getId());
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
                        displayName(names, returned.getActorUserId()),
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
                            displayName(names, ready.getAssigneeUserId()));
            result.put(exercise.getId(), new ReviewProgress(
                    submission.getCurrentStep(),
                    role,
                    reviewer,
                    null,
                    exercise.getSubmittedAt()));
        }
        return result;
    }

    private static Map<UUID, OfficialPackage> currentOrLatestPackage(List<OfficialPackage> packages) {
        Map<UUID, OfficialPackage> result = new HashMap<>();
        for (OfficialPackage pkg : packages) {
            OfficialPackage existing = result.get(pkg.getExerciseId());
            if (existing == null
                    || (pkg.isCurrent() && !existing.isCurrent())
                    || (pkg.isCurrent() == existing.isCurrent()
                            && pkg.getPackageVersion() > existing.getPackageVersion())) {
                result.put(pkg.getExerciseId(), pkg);
            }
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

    private static String displayName(Map<UUID, String> names, UUID userId) {
        if (userId == null) {
            return null;
        }
        return names.get(userId);
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
        if ("VALIDATED".equals(exercise.getWorkflowStatus())) {
            return exercise.getValidatedAt();
        }
        if ("ARCHIVED".equals(exercise.getWorkflowStatus())) {
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

    /**
     * Create Exercise request payload.
     */
    public record CreateExercise(
            @NotNull UUID toolkitId,
            @NotBlank @Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$") String sizingMonth,
            @NotNull LocalDate slotStartDate,
            @Min(1) @Max(12) short slotWeeks,
            @NotNull LocalDate tmsFrom,
            @NotNull LocalDate tmsTo) {
    }

    /**
     * Create Exercise response with initialization notices for the Supervisor.
     */
    public record CreateExerciseResult(Exercise exercise, List<String> notices) {
    }

    /**
     * Update Exercise period payload (Toolkit is immutable after create).
     */
    public record UpdateExercisePeriods(
            @NotBlank @Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$") String sizingMonth,
            @NotNull LocalDate slotStartDate,
            @Min(1) @Max(12) short slotWeeks,
            @NotNull LocalDate tmsFrom,
            @NotNull LocalDate tmsTo) {
    }

    /**
     * Update periods response with optional holiday re-apply notices.
     */
    public record UpdateExercisePeriodsResult(Exercise exercise, List<String> notices) {
    }

    /**
     * Frozen Subtask view.
     */
    public record ExerciseSubtaskView(
            UUID id, UUID sourceToolkitSubtaskId, String name, String description,
            int displayOrder, Instant deletedAt) {
    }

    /**
     * Frozen Shared KPI view.
     */
    public record ExerciseKpiView(
            UUID id, UUID sourceSelectionId, String carrier, String site,
            String customerCountry, BigDecimal deliveryHc, boolean valid) {
    }

    /**
     * Frozen Toolkit view.
     */
    public record ExerciseToolkitView(
            UUID id, String name, String center, String domain, String pl1, String pl2,
            String pl3Code, String pl3Name, boolean combineSubtasksTime, long version) {
    }

    /**
     * Exercise snapshot envelope.
     */
    public record ExerciseSnapshot(
            ExerciseToolkitView toolkit, List<ExerciseSubtaskView> subtasks,
            List<ExerciseKpiView> sharedKpis, LocalDate timesheetSyncDate) {
    }

    /**
     * Exercise API response including action flags.
     */
    public record Exercise(
            UUID id, String exerciseCode, UUID toolkitId, String sizingMonth,
            LocalDate slotStartDate, short slotWeeks, LocalDate tmsFrom, LocalDate tmsTo,
            String workflowStatus, UUID officialScenarioId, Instant submittedAt,
            boolean canDelete, boolean canSubmit, boolean canEdit,
            long version, Instant createdAt,
            Short currentStep, String requiredRole, String currentReviewer, String lastDecisionComment,
            BigDecimal deliveryHc, BigDecimal rightSizingHc, BigDecimal productionSupport,
            BigDecimal capacityCreation, Integer agingDays, Instant archivedAt,
            ExerciseSnapshot snapshot) {
    }

    /**
     * Supervisor Exercise list query (tab + field filters).
     */
    public record ListQuery(
            String tab,
            String exerciseCode,
            String toolkitName,
            String pl3Name,
            String workflowStatus,
            String reviewStage,
            String handler,
            String officialScenario,
            LocalDate createdFrom,
            LocalDate createdTo,
            LocalDate submittedFrom,
            LocalDate submittedTo,
            LocalDate archivedFrom,
            LocalDate archivedTo) {
    }

    /**
     * Supervisor Exercise list response: filtered rows and filter options for the tab.
     */
    public record ExerciseListView(
            List<Exercise> items,
            List<String> toolkitNames,
            List<String> pl3Names,
            List<String> reviewerNames) {
    }
}
