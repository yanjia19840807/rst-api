package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseCalendar;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseCalendarRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseToolkitSnapshot;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitRepository;
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
            Clock clock) {
        this.exercises = exercises;
        this.toolkits = toolkits;
        this.timesheet = timesheet;
        this.syncRuns = syncRuns;
        this.teamSetups = teamSetups;
        this.calendars = calendars;
        this.initialization = initialization;
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
        // Recompute under a repeatable-read transaction so an ACTIVE switch cannot mix snapshots.
        preview(ccgid, request);
        var toolkit = toolkits.findActiveById(request.toolkitId())
                .orElseThrow(() -> notFound("toolkit-not-found", "The Toolkit was not found."));
        if (!timesheet.supervisorOwnsScope(
                ccgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "toolkit-out-of-scope",
                    "The current Supervisor does not own the Toolkit scope.");
        }
        var active = timesheet.activeSnapshot();
        var selections = toolkit.getSharedKpiSelections().stream()
                .filter(selection -> selection.getDeletedAt() == null)
                .toList();
        if (selections.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "shared-kpi-selection-required",
                    "Exercise requires at least one active Shared KPI selection.");
        }
        var countries = selections.stream()
                .map(selection -> selection.getCustomerCountry())
                .distinct()
                .toList();
        var candidates = timesheet.kpis(
                toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), countries);
        for (var selection : selections) {
            boolean exists = candidates.stream().anyMatch(candidate ->
                    Objects.equals(candidate.carrier(), selection.getCarrier())
                            && candidate.site().equals(selection.getSite())
                            && candidate.customerCountry().equals(selection.getCustomerCountry()));
            if (!exists) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "stale-shared-kpi-selection",
                        "A selected Shared KPI no longer exists in the ACTIVE Timesheet snapshot.");
            }
        }

        Instant now = clock.instant();
        UUID exerciseId = UUID.randomUUID();
        String code = "EX-" + LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + exerciseId.toString().substring(0, 8).toUpperCase(Locale.ROOT);

        RstExercise exercise = RstExercise.create(
                exerciseId,
                code,
                toolkit.getId(),
                ownerId,
                request.sizingMonth(),
                request.slotStartDate(),
                request.slotWeeks(),
                request.tmsFrom(),
                request.tmsTo(),
                now);
        exercise.freezeToolkitSnapshot(
                toolkit.getId(),
                toolkit.getVersion(),
                active.id(),
                toolkit.getName(),
                toolkit.getSupervisorPositionId(),
                toolkit.getCenter(),
                toolkit.getDomain(),
                toolkit.getPl1(),
                toolkit.getPl2(),
                toolkit.getPrimaryPl3Code(),
                toolkit.getPl3Name(),
                toolkit.isCombineSubtasksTime(),
                ownerId,
                now);
        for (var subtask : toolkit.getSubtasks()) {
            exercise.addSubtask(
                    subtask.getId(),
                    subtask.getName(),
                    subtask.getDescription(),
                    subtask.getDisplayOrder(),
                    now);
        }
        for (var selection : selections) {
            BigDecimal hc = timesheet.headcount(
                    toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(),
                    selection.getCarrier(), selection.getSite(), selection.getCustomerCountry());
            exercise.addSharedKpiLine(
                    selection.getId(),
                    active.id(),
                    toolkit.getCenter(),
                    selection.getSite(),
                    toolkit.getDomain(),
                    toolkit.getPl1(),
                    toolkit.getPl2(),
                    toolkit.getPrimaryPl3Code(),
                    toolkit.getPl3Name(),
                    selection.getCarrier(),
                    selection.getCustomerCountry(),
                    hc,
                    ownerId,
                    now);
        }
        exercises.saveAndFlush(exercise);
        teamSetups.save(ExerciseTeamSetup.emptyShell(exerciseId, ownerId, now));
        calendars.save(ExerciseCalendar.emptyShell(exerciseId, ownerId, now));
        List<String> notices = new ArrayList<>(initialization.initialize(exercise, ownerId));
        return new CreateExerciseResult(toResponse(exercise), notices);
    }

    /**
     * Lists non-deleted Exercises owned by the Supervisor.
     *
     * @param ownerId Supervisor user id
     * @return Exercise responses
     */
    @Transactional(readOnly = true)
    public List<Exercise> list(UUID ownerId) {
        return exercises.findByOwnerUserIdAndDeletedAtIsNullOrderByUpdatedAtDescIdAsc(ownerId)
                .stream()
                .map(this::toResponse)
                .toList();
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
        return toResponse(requireOwned(ownerId, exerciseId));
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
     * Previews the Toolkit/Timesheet freeze without persisting an Exercise.
     *
     * @param ccgid Supervisor CCGID
     * @param request create payload
     * @return preview snapshot
     */
    @Transactional(readOnly = true)
    public ExerciseSnapshot preview(String ccgid, CreateExercise request) {
        if (request.tmsTo().isBefore(request.tmsFrom())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-tms-period",
                    "tmsTo cannot be before tmsFrom.");
        }
        var toolkit = toolkits.findActiveById(request.toolkitId())
                .orElseThrow(() -> notFound("toolkit-not-found", "The Toolkit was not found."));
        if (!timesheet.supervisorOwnsScope(
                ccgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "toolkit-out-of-scope",
                    "The current Supervisor does not own the Toolkit scope.");
        }
        var active = timesheet.activeSnapshot();
        var selections = toolkit.getSharedKpiSelections().stream()
                .filter(selection -> selection.getDeletedAt() == null)
                .toList();
        if (selections.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "shared-kpi-selection-required",
                    "Exercise requires at least one active Shared KPI selection.");
        }
        var countries = selections.stream()
                .map(selection -> selection.getCustomerCountry())
                .distinct()
                .toList();
        var candidates = timesheet.kpis(
                toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), countries);
        var kpis = selections.stream().map(selection -> {
            var candidate = candidates.stream().filter(item ->
                            Objects.equals(item.carrier(), selection.getCarrier())
                                    && item.site().equals(selection.getSite())
                                    && item.customerCountry().equals(selection.getCustomerCountry()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT,
                            "stale-shared-kpi-selection",
                            "A selected Shared KPI no longer exists in the ACTIVE Timesheet snapshot."));
            return new ExerciseKpiView(
                    selection.getId(), selection.getId(), selection.getCarrier(),
                    selection.getSite(), selection.getCustomerCountry(),
                    candidate.deliveryHc(), true);
        }).toList();
        var subtasks = toolkit.getSubtasks().stream()
                .map(item -> new ExerciseSubtaskView(
                        item.getId(), item.getId(), item.getName(), item.getDescription(),
                        item.getDisplayOrder(), null))
                .toList();
        if (subtasks.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-toolkit-snapshot",
                    "Exercise requires at least one active Subtask.");
        }
        return new ExerciseSnapshot(
                new ExerciseToolkitView(
                        toolkit.getId(), toolkit.getName(), toolkit.getCenter(), toolkit.getDomain(),
                        toolkit.getPl1(), toolkit.getPl2(), toolkit.getPrimaryPl3Code(),
                        toolkit.getPl3Name(), toolkit.isCombineSubtasksTime(), toolkit.getVersion()),
                subtasks, kpis, active.syncDate());
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

    private Exercise toResponse(RstExercise exercise) {
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
        return new Exercise(
                exercise.getId(),
                exercise.getExerciseCode(),
                snapshot.getSourceToolkitId(),
                exercise.getSizingMonth(),
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
                new ExerciseSnapshot(toolkitView, subtasks, kpis, syncDate));
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
            @Min(1) @Max(53) short slotWeeks,
            @NotNull LocalDate tmsFrom,
            @NotNull LocalDate tmsTo) {
    }

    /**
     * Create Exercise response with initialization notices for the Supervisor.
     */
    public record CreateExerciseResult(Exercise exercise, List<String> notices) {
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
            long version, Instant createdAt, ExerciseSnapshot snapshot) {
    }
}
