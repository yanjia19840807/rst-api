package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitAssociatedDataService;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitVolumeService;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.ToolkitVolumeGrid;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitHoliday;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitTeamSetup;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.VolumeTrainWindows;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.VolumeTrainWindows.SlotBound;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeDailyInput;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeMonthlyInput;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeSlotInput;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeSlotInputRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.cycletime.application.SystemCycleTimeBaselineWriter;
import com.cmacgm.gbs.rst.api.exercise.cycletime.domain.ExerciseTmsSession;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.ExerciseTmsSessionRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionRepository;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionSpecification;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionSpecification.Filter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single orchestration entry for Exercise Associated Data initialization and period refresh:
 * Toolkit latest-state seed, volume grids, holidays, and TMS population.
 */
@Service
public class ExerciseInitializationService {

    private final ExerciseTeamSetupRepository teamSetups;
    private final ExerciseHolidayRepository holidays;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseVolumeMonthlyInputRepository monthlyVolumes;
    private final ExerciseVolumeDailyInputRepository dailyVolumes;
    private final ExerciseVolumeSlotInputRepository slotVolumes;
    private final TmsSessionRepository tmsSessions;
    private final ExerciseTmsSessionRepository exerciseTmsSessions;
    private final SystemCycleTimeBaselineWriter systemCycleTime;
    private final ToolkitVolumeService toolkitVolumes;
    private final ToolkitAssociatedDataService toolkitAssociatedData;
    private final Clock clock;

    public ExerciseInitializationService(
            ExerciseTeamSetupRepository teamSetups,
            ExerciseHolidayRepository holidays,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseVolumeMonthlyInputRepository monthlyVolumes,
            ExerciseVolumeDailyInputRepository dailyVolumes,
            ExerciseVolumeSlotInputRepository slotVolumes,
            TmsSessionRepository tmsSessions,
            ExerciseTmsSessionRepository exerciseTmsSessions,
            SystemCycleTimeBaselineWriter systemCycleTime,
            ToolkitVolumeService toolkitVolumes,
            ToolkitAssociatedDataService toolkitAssociatedData,
            Clock clock) {
        this.teamSetups = teamSetups;
        this.holidays = holidays;
        this.supportItems = supportItems;
        this.monthlyVolumes = monthlyVolumes;
        this.dailyVolumes = dailyVolumes;
        this.slotVolumes = slotVolumes;
        this.tmsSessions = tmsSessions;
        this.exerciseTmsSessions = exerciseTmsSessions;
        this.systemCycleTime = systemCycleTime;
        this.toolkitVolumes = toolkitVolumes;
        this.toolkitAssociatedData = toolkitAssociatedData;
        this.clock = clock;
    }

    /**
     * Initializes Associated Data for a newly created Exercise.
     */
    @Transactional
    public List<String> initialize(RstExercise exercise, String actorCcgid) {
        Instant now = clock.instant();
        List<String> notices = new ArrayList<>();

        if (seedFromToolkit(exercise, actorCcgid, now)) {
            notices.add("Team Setup, Production Support, and Calendar copied from the Toolkit.");
        } else {
            notices.add(
                    "No Toolkit Associated Data yet. "
                            + "Team Setup, Production Support, and Calendar start empty.");
        }

        replaceTrainVolumeGridsFromToolkit(exercise, actorCcgid);
        notices.add("Volume Input filled from Toolkit history for this Sizing Month.");

        if (exercise.hasTmsPeriod()) {
            notices.add(syncTmsPopulation(exercise, actorCcgid));
        }
        return notices;
    }

    /**
     * Replaces Monthly / Daily Volume with the Toolkit overlay for the current Sizing Month.
     * Exercise edits are discarded. Slot Volume is not touched.
     */
    @Transactional
    public void replaceTrainVolumeGridsFromToolkit(RstExercise exercise, String actorCcgid) {
        Instant now = clock.instant();
        replaceMonthlyFromToolkit(exercise, actorCcgid, now);
        replaceDailyFromToolkit(exercise, actorCcgid, now);
    }

    /**
     * Reconciles Embedded TMS population for the Exercise TMS period and refreshes SYSTEM CT
     * when the active baseline is absent or SYSTEM.
     */
    @Transactional
    public String syncTmsPopulation(RstExercise exercise, String actorCcgid) {
        return syncTmsPopulation(exercise, actorCcgid, false);
    }

    /**
     * Reconciles Embedded TMS population for the Exercise TMS period.
     *
     * @param forceSystem when true, replaces an active MANUAL baseline with SYSTEM
     */
    @Transactional
    public String syncTmsPopulation(RstExercise exercise, String actorCcgid, boolean forceSystem) {
        if (!exercise.hasTmsPeriod()) {
            return "TMS period is not set. Select it in Associated Data when using the SYSTEM median.";
        }
        Instant now = clock.instant();
        UUID exerciseId = exercise.getId();
        List<TmsSession> qualifying = tmsSessions.findAll(TmsSessionSpecification.filtered(new Filter(
                null,
                null,
                exercise.getToolkitId(),
                null,
                TmsSessionStatus.COMPLETED,
                null,
                null,
                null,
                exercise.getTmsFrom(),
                exercise.getTmsTo(),
                true)));

        Set<UUID> desiredIds = new HashSet<>();
        for (TmsSession session : qualifying) {
            desiredIds.add(session.getId());
        }

        List<ExerciseTmsSession> existing = exerciseTmsSessions.findByExerciseId(exerciseId);
        Map<UUID, ExerciseTmsSession> existingBySessionId = new HashMap<>();
        List<ExerciseTmsSession> obsolete = new ArrayList<>();
        for (ExerciseTmsSession link : existing) {
            existingBySessionId.put(link.getTmsSessionId(), link);
            if (!desiredIds.contains(link.getTmsSessionId())) {
                obsolete.add(link);
            }
        }
        if (!obsolete.isEmpty()) {
            exerciseTmsSessions.deleteAllInBatch(obsolete);
        }

        List<ExerciseTmsSession> missing = new ArrayList<>();
        for (UUID sessionId : desiredIds) {
            if (!existingBySessionId.containsKey(sessionId)) {
                missing.add(ExerciseTmsSession.select(
                        exerciseId, sessionId, true, null, actorCcgid, now));
            }
        }
        if (!missing.isEmpty()) {
            exerciseTmsSessions.saveAll(missing);
        }
        exerciseTmsSessions.flush();

        if (forceSystem) {
            systemCycleTime.refreshForcingSystem(exerciseId, actorCcgid);
        } else {
            systemCycleTime.refreshIfSystemOrAbsent(exerciseId, actorCcgid);
        }
        return "Linked " + desiredIds.size()
                + " COMPLETED TMS session(s) for the Exercise TMS period.";
    }

    /**
     * Unlinks Embedded TMS sessions and drops an active SYSTEM baseline.
     */
    @Transactional
    public String clearTmsPopulation(RstExercise exercise) {
        UUID exerciseId = exercise.getId();
        exerciseTmsSessions.deleteByExerciseId(exerciseId);
        exerciseTmsSessions.flush();
        systemCycleTime.deactivateSystemIfActive(exerciseId);
        return "TMS period cleared. Linked sessions and the SYSTEM median were removed.";
    }

    private boolean seedFromToolkit(RstExercise target, String actorCcgid, Instant now) {
        Optional<ToolkitTeamSetup> sourceTeam = toolkitAssociatedData.findTeamSetup(target.getToolkitId());
        if (sourceTeam.isEmpty()) {
            return false;
        }
        copyTeamSetup(sourceTeam.get(), target.getId(), actorCcgid, now);
        copySupport(target, actorCcgid, now);
        copyHolidays(target.getToolkitId(), target.getId(), actorCcgid, now);
        return true;
    }

    private void copyTeamSetup(ToolkitTeamSetup source, UUID targetId, String actorCcgid, Instant now) {
        ExerciseTeamSetup target = teamSetups.findById(targetId)
                .orElseThrow(() -> initializationConflict(
                        "exercise-team-setup-shell-missing",
                        "The target Exercise Team Setup shell is missing."));
        target.replaceInputs(source.toInput(), actorCcgid, now);
        teamSetups.save(target);
    }

    private void copySupport(RstExercise target, String actorCcgid, Instant now) {
        List<ExerciseProductionSupportItem> copies = toolkitAssociatedData
                .listSupport(target.getToolkitId())
                .stream()
                .map(sourceItem -> ExerciseProductionSupportItem.createFromToolkit(
                        target.getId(),
                        sourceItem,
                        actorCcgid,
                        now))
                .toList();
        supportItems.saveAll(copies);
    }

    private void copyHolidays(UUID toolkitId, UUID targetId, String actorCcgid, Instant now) {
        Set<LocalDate> existingDates = holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(targetId)
                .stream()
                .map(ExerciseHoliday::getHolidayDate)
                .collect(Collectors.toSet());
        List<ExerciseHoliday> copies = new ArrayList<>();
        for (ToolkitHoliday holiday : toolkitAssociatedData.listHolidays(toolkitId)) {
            LocalDate date = holiday.getHolidayDate();
            if (date == null || !existingDates.add(date)) {
                continue;
            }
            copies.add(ExerciseHoliday.create(
                    targetId,
                    date,
                    holiday.getHolidayName(),
                    holiday.getHolidayType(),
                    actorCcgid,
                    now));
        }
        holidays.saveAll(copies);
    }

    private void replaceMonthlyFromToolkit(RstExercise exercise, String actorCcgid, Instant now) {
        UUID targetId = exercise.getId();
        monthlyVolumes.deleteByExerciseId(targetId);
        monthlyVolumes.flush();
        List<ExerciseVolumeMonthlyInput> rows = new ArrayList<>();
        for (var request : ToolkitVolumeGrid.monthlyOverlay(
                toolkitVolumes.monthlySeedByMonth(exercise.getToolkitId()),
                exercise.getSizingMonth())) {
            BigDecimal actual = request.actualVolume();
            rows.add(ExerciseVolumeMonthlyInput.create(
                    targetId,
                    MonthKeys.parseMonthStart(request.month()),
                    actual,
                    request.commercialRatio(),
                    actual != null ? "TOOLKIT" : "MANUAL",
                    null,
                    actorCcgid,
                    now));
        }
        monthlyVolumes.saveAll(rows);
        monthlyVolumes.flush();
    }

    private void replaceDailyFromToolkit(RstExercise exercise, String actorCcgid, Instant now) {
        UUID targetId = exercise.getId();
        dailyVolumes.deleteByExerciseId(targetId);
        dailyVolumes.flush();
        List<ExerciseVolumeDailyInput> rows = new ArrayList<>();
        for (var request : ToolkitVolumeGrid.dailyOverlay(
                toolkitVolumes.dailySeedByDate(exercise.getToolkitId()),
                exercise.getSizingMonth())) {
            BigDecimal actual = request.actualVolume();
            rows.add(ExerciseVolumeDailyInput.create(
                    targetId,
                    request.volumeDate(),
                    actual,
                    request.dailyAdjustmentRatio(),
                    actual != null ? "TOOLKIT" : "MANUAL",
                    null,
                    actorCcgid,
                    now));
        }
        dailyVolumes.saveAll(rows);
        dailyVolumes.flush();
    }

    /**
     * Wipes existing slot rows and generates an empty Per-slot grid for the current Slot Period.
     */
    @Transactional
    public List<ExerciseVolumeSlotInput> replaceEmptySlotGrid(RstExercise exercise, String actorCcgid) {
        if (!exercise.hasSlotPeriod()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "slot-period-required",
                    "Set a Slot Period to generate the per-slot grid.");
        }
        Instant now = clock.instant();
        UUID targetId = exercise.getId();
        slotVolumes.deleteByExerciseId(targetId);
        slotVolumes.flush();
        List<SlotBound> expected = VolumeTrainWindows.slotTrainBounds(
                exercise.getSlotStartDate(), exercise.getSlotWeeks());
        Map<Instant, BigDecimal> seed = toolkitVolumes.slotSeedByStart(exercise.getToolkitId());
        List<ExerciseVolumeSlotInput> rows = new ArrayList<>(expected.size());
        for (SlotBound bound : expected) {
            BigDecimal actual = seed.get(bound.start());
            ExerciseVolumeSlotInput row = ExerciseVolumeSlotInput.create(
                    targetId,
                    bound.start(),
                    bound.end(),
                    actual,
                    actual != null ? "TOOLKIT" : "MANUAL",
                    null,
                    actorCcgid,
                    now);
            rows.add(row);
        }
        slotVolumes.saveAll(rows);
        slotVolumes.flush();
        return slotVolumes.findByExerciseIdOrderBySlotStartAtAsc(targetId);
    }

    /**
     * Deletes all Per-slot Volume rows when the Slot Period is cleared.
     */
    @Transactional
    public void clearSlotGrid(UUID exerciseId) {
        slotVolumes.deleteByExerciseId(exerciseId);
        slotVolumes.flush();
    }

    private static ApiException initializationConflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

}
