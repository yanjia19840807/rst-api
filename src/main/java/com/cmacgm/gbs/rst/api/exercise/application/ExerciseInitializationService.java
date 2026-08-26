package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitVolumeService.VolumeSeed;
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
import com.cmacgm.gbs.rst.api.common.workingdays.HolidayDayKind;
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
            notices.add(
                    "Associated Data seeded from Toolkit latest state "
                            + "(Team Setup, Production Support, Calendar).");
        } else {
            notices.add(
                    "No Toolkit latest state yet. "
                            + "Associated Data starts empty. Add holiday dates in Calendar if needed.");
        }

        syncVolumeGrids(exercise, actorCcgid);
        notices.add("Volume Input pre-filled from Toolkit volume when available.");

        notices.add(syncTmsPopulation(exercise, actorCcgid));
        return notices;
    }

    /**
     * Reconciles Volume Input grids after Exercise period changes (does not re-seed AD snapshots).
     */
    @Transactional
    public void ensureTrainVolumeGrids(RstExercise exercise, String actorCcgid) {
        syncVolumeGrids(exercise, actorCcgid);
    }

    /**
     * Reconciles Embedded TMS population for the Exercise TMS period and refreshes SYSTEM CT.
     */
    @Transactional
    public String syncTmsPopulation(RstExercise exercise, String actorCcgid) {
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
                exercise.getTmsTo())));

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

        systemCycleTime.refreshIfSystemOrAbsent(exerciseId, actorCcgid);
        return "Linked " + desiredIds.size()
                + " COMPLETED TMS session(s) for the Exercise TMS period.";
    }

    private boolean seedFromToolkit(RstExercise target, String actorCcgid, Instant now) {
        Optional<ToolkitTeamSetup> sourceTeam = toolkitAssociatedData.findTeamSetup(target.getToolkitId());
        if (sourceTeam.isEmpty()) {
            return false;
        }
        copyTeamSetup(sourceTeam.get(), target.getId(), actorCcgid, now);
        copySupport(target, actorCcgid, now);
        copyHolidays(target.getToolkitId(), target.getId(), actorCcgid, now);
        target.markInitializedFrom(sourceTeam.get().getSourceExerciseId(), actorCcgid, now);
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
                    HolidayDayKind.parse(holiday.getHolidayType()).name(),
                    actorCcgid,
                    now));
        }
        holidays.saveAll(copies);
    }

    private void syncVolumeGrids(RstExercise exercise, String actorCcgid) {
        Instant now = clock.instant();
        syncMonthly(exercise, actorCcgid, now);
        syncDaily(exercise, actorCcgid, now);
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

    private void syncMonthly(RstExercise exercise, String actorCcgid, Instant now) {
        UUID targetId = exercise.getId();
        YearMonth sizingYm = YearMonth.from(exercise.getSizingMonth());
        List<ExerciseVolumeMonthlyInput> targetRows =
                monthlyVolumes.findByExerciseIdOrderByMonthAsc(targetId);
        Map<LocalDate, ExerciseVolumeMonthlyInput> targetByKey = new HashMap<>();
        targetRows.forEach(row -> targetByKey.put(row.getMonth(), row));
        Map<LocalDate, VolumeSeed> seed = toolkitVolumes.monthlySeedByMonth(exercise.getToolkitId());
        YearMonth min = null;
        YearMonth max = null;
        for (LocalDate month : targetByKey.keySet()) {
            YearMonth ym = YearMonth.from(month);
            if (ym.isAfter(sizingYm)) {
                continue;
            }
            if (min == null || ym.isBefore(min)) {
                min = ym;
            }
            if (max == null || ym.isAfter(max)) {
                max = ym;
            }
        }
        for (LocalDate month : seed.keySet()) {
            YearMonth ym = YearMonth.from(month);
            if (ym.isAfter(sizingYm)) {
                continue;
            }
            if (min == null || ym.isBefore(min)) {
                min = ym;
            }
            if (max == null || ym.isAfter(max)) {
                max = ym;
            }
        }
        List<ExerciseVolumeMonthlyInput> missing = new ArrayList<>();
        if (min != null && max != null) {
            for (YearMonth ym = min; !ym.isAfter(max); ym = ym.plusMonths(1)) {
                LocalDate month = ym.atDay(1);
                if (targetByKey.containsKey(month)) {
                    continue;
                }
                VolumeSeed point = seed.get(month);
                BigDecimal actual = point == null ? null : point.actualVolume();
                missing.add(ExerciseVolumeMonthlyInput.create(
                        targetId,
                        month,
                        actual,
                        point == null ? null : point.ratio(),
                        actual != null ? "TOOLKIT" : "MANUAL",
                        null,
                        actorCcgid,
                        now));
            }
        }
        monthlyVolumes.deleteAllInBatch(targetRows.stream()
                .filter(row -> YearMonth.from(row.getMonth()).isAfter(sizingYm))
                .toList());
        monthlyVolumes.saveAll(missing);
    }

    private void syncDaily(RstExercise exercise, String actorCcgid, Instant now) {
        UUID targetId = exercise.getId();
        LocalDate cutoff = YearMonth.from(exercise.getSizingMonth()).atEndOfMonth();
        List<ExerciseVolumeDailyInput> targetRows =
                dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(targetId);
        Map<LocalDate, ExerciseVolumeDailyInput> targetByKey = new HashMap<>();
        targetRows.forEach(row -> targetByKey.put(row.getVolumeDate(), row));
        Map<LocalDate, VolumeSeed> seed = toolkitVolumes.dailySeedByDate(exercise.getToolkitId());
        LocalDate min = null;
        LocalDate max = null;
        for (LocalDate date : targetByKey.keySet()) {
            if (date.isAfter(cutoff)) {
                continue;
            }
            if (min == null || date.isBefore(min)) {
                min = date;
            }
            if (max == null || date.isAfter(max)) {
                max = date;
            }
        }
        for (LocalDate date : seed.keySet()) {
            if (date.isAfter(cutoff)) {
                continue;
            }
            if (min == null || date.isBefore(min)) {
                min = date;
            }
            if (max == null || date.isAfter(max)) {
                max = date;
            }
        }
        List<ExerciseVolumeDailyInput> missing = new ArrayList<>();
        if (min != null && max != null) {
            for (LocalDate date = min; !date.isAfter(max); date = date.plusDays(1)) {
                if (targetByKey.containsKey(date)) {
                    continue;
                }
                VolumeSeed point = seed.get(date);
                BigDecimal actual = point == null ? null : point.actualVolume();
                missing.add(ExerciseVolumeDailyInput.create(
                        targetId,
                        date,
                        actual,
                        point == null ? null : point.ratio(),
                        actual != null ? "TOOLKIT" : "MANUAL",
                        null,
                        actorCcgid,
                        now));
            }
        }
        dailyVolumes.deleteAllInBatch(targetRows.stream()
                .filter(row -> row.getVolumeDate().isAfter(cutoff))
                .toList());
        dailyVolumes.saveAll(missing);
    }

    private static ApiException initializationConflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

}
