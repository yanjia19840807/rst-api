package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitVolumeService;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.VolumeTrainWindows;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.VolumeTrainWindows.SlotBound;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup.TeamSetupInput;
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
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.common.workingdays.HolidayDayKind;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionRepository;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionSpecification;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionSpecification.Filter;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single orchestration entry for Exercise Associated Data initialization and period refresh:
 * archive seed, volume grids, holidays, and TMS population.
 */
@Service
public class ExerciseInitializationService {


    private final RstExerciseRepository exercises;
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
    private final Clock clock;

    public ExerciseInitializationService(
            RstExerciseRepository exercises,
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
            Clock clock) {
        this.exercises = exercises;
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
        this.clock = clock;
    }

    /**
     * Initializes Associated Data for a newly created Exercise.
     */
    @Transactional
    public List<String> initialize(RstExercise exercise, String actorCcgid) {
        Instant now = clock.instant();
        List<String> notices = new ArrayList<>();
        Set<Short> holidayYears = resolveHolidayYears(exercise);
        short primaryYear = primaryYear(exercise.getSizingMonth());

        Optional<RstExercise> archive = findLatestArchive(exercise.getToolkitId());
        if (archive.isPresent()) {
            RstExercise source = archive.get();
            seedFromArchive(source, exercise, actorCcgid, now);
            notices.add("Associated Data seeded from archived exercise "
                    + source.getExerciseCode()
                    + " (Team Setup, Production Support, Calendar & Holidays).");
            short archivePrimary = primaryYear(source.getSizingMonth());
            if (archivePrimary != primaryYear) {
                notices.add("Sizing year differs from the archive ("
                        + archivePrimary + " → " + primaryYear
                        + "). Holidays were copied for " + formatYears(holidayYears) + ".");
            }
            copyHolidays(source.getId(), exercise.getId(), holidayYears, actorCcgid, now);
        } else {
            notices.add(
                    "No approved exercise found for this Toolkit. "
                            + "Associated Data starts empty. Add holiday dates in Calendar if needed.");
        }

        syncVolumeGrids(exercise, archive.map(RstExercise::getId).orElse(null), actorCcgid);
        notices.add("Volume Input pre-filled from Toolkit volume when available.");

        notices.add(syncTmsPopulation(exercise, actorCcgid));
        return notices;
    }

    /**
     * Reconciles Volume Input grids after Exercise period changes (no archive re-seed).
     */
    @Transactional
    public void ensureTrainVolumeGrids(RstExercise exercise, String actorCcgid) {
        syncVolumeGrids(exercise, null, actorCcgid);
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

    public static Set<Short> resolveHolidayYears(RstExercise exercise) {
        Set<Short> years = new LinkedHashSet<>();
        years.add(primaryYear(exercise.getSizingMonth()));
        addYearRange(years, exercise.getTmsFrom(), exercise.getTmsTo());
        addYearRange(years, exercise.getSlotStartDate(), slotEnd(exercise));
        return years;
    }

    private Optional<RstExercise> findLatestArchive(UUID toolkitId) {
        return exercises.findApprovedByToolkit(toolkitId, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    private void seedFromArchive(
            RstExercise source,
            RstExercise target,
            String actorCcgid,
            Instant now) {
        copyTeamSetup(source.getId(), target.getId(), actorCcgid, now);
        copySupport(source, target, actorCcgid, now);
        target.markInitializedFrom(source.getId(), actorCcgid, now);
    }

    private void copyTeamSetup(UUID sourceId, UUID targetId, String actorCcgid, Instant now) {
        ExerciseTeamSetup target = teamSetups.findById(targetId)
                .orElseThrow(() -> initializationConflict(
                        "exercise-team-setup-shell-missing",
                        "The target Exercise Team Setup shell is missing."));
        ExerciseTeamSetup source = teamSetups.findById(sourceId)
                .orElseThrow(() -> initializationConflict(
                        "archive-team-setup-missing",
                        "The archived Exercise has no Team Setup to copy."));
        target.replaceInputs(toInput(source), actorCcgid, now);
        teamSetups.save(target);
    }

    private void copySupport(RstExercise source, RstExercise target, String actorCcgid, Instant now) {
        List<ExerciseProductionSupportItem> copies = supportItems
                .findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(source.getId())
                .stream()
                .map(sourceItem -> ExerciseProductionSupportItem.createFromArchive(
                        target.getId(),
                        sourceItem,
                        actorCcgid,
                        now))
                .toList();
        supportItems.saveAll(copies);
    }

    private void copyHolidays(
            UUID sourceId,
            UUID targetId,
            Set<Short> holidayYears,
            String actorCcgid,
            Instant now) {
        Set<LocalDate> existingDates = holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(targetId)
                .stream()
                .map(ExerciseHoliday::getHolidayDate)
                .collect(Collectors.toSet());
        List<ExerciseHoliday> copies = new ArrayList<>();
        for (ExerciseHoliday holiday : holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(sourceId)) {
            LocalDate date = holiday.getHolidayDate();
            if (date == null || !holidayYears.contains((short) date.getYear())) {
                continue;
            }
            if (existingDates.add(date)) {
                copies.add(ExerciseHoliday.create(
                        targetId,
                        date,
                        holiday.getHolidayName(),
                        HolidayDayKind.parse(holiday.getHolidayType()).name(),
                        actorCcgid,
                        now));
            }
        }
        holidays.saveAll(copies);
    }

    private void syncVolumeGrids(RstExercise exercise, UUID archiveExerciseId, String actorCcgid) {
        Instant now = clock.instant();
        syncMonthly(exercise, actorCcgid, now);
        syncDaily(exercise, actorCcgid, now);
        syncSlot(exercise, archiveExerciseId, actorCcgid, now);
    }

    private void syncMonthly(RstExercise exercise, String actorCcgid, Instant now) {
        UUID targetId = exercise.getId();
        YearMonth sizingYm = YearMonth.from(exercise.getSizingMonth());
        List<ExerciseVolumeMonthlyInput> targetRows =
                monthlyVolumes.findByExerciseIdOrderByMonthAsc(targetId);
        Map<LocalDate, ExerciseVolumeMonthlyInput> targetByKey = new HashMap<>();
        targetRows.forEach(row -> targetByKey.put(row.getMonth(), row));
        Map<LocalDate, BigDecimal> seed = toolkitVolumes.monthlySeedByMonth(exercise.getToolkitId());
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
                BigDecimal actual = seed.get(month);
                missing.add(ExerciseVolumeMonthlyInput.create(
                        targetId,
                        month,
                        actual,
                        null,
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
        Map<LocalDate, BigDecimal> seed = toolkitVolumes.dailySeedByDate(exercise.getToolkitId());
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
                BigDecimal actual = seed.get(date);
                missing.add(ExerciseVolumeDailyInput.create(
                        targetId,
                        date,
                        actual,
                        null,
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

    private void syncSlot(
            RstExercise exercise, UUID archiveExerciseId, String actorCcgid, Instant now) {
        UUID targetId = exercise.getId();
        List<SlotBound> expected = VolumeTrainWindows.slotTrainBounds(
                exercise.getSlotStartDate(), exercise.getSlotWeeks());
        Set<SlotKey> expectedKeys = expected.stream()
                .map(bound -> new SlotKey(bound.start(), bound.end()))
                .collect(Collectors.toSet());
        List<ExerciseVolumeSlotInput> targetRows =
                slotVolumes.findByExerciseIdOrderBySlotStartAtAsc(targetId);
        Map<SlotKey, ExerciseVolumeSlotInput> targetByKey = new HashMap<>();
        targetRows.forEach(row -> targetByKey.put(
                new SlotKey(row.getSlotStartAt(), row.getSlotEndAt()), row));
        Map<SlotKey, ExerciseVolumeSlotInput> archiveByKey = new HashMap<>();
        if (archiveExerciseId != null) {
            slotVolumes.findByExerciseIdOrderBySlotStartAtAsc(archiveExerciseId)
                    .forEach(row -> archiveByKey.put(
                            new SlotKey(row.getSlotStartAt(), row.getSlotEndAt()), row));
        }

        List<ExerciseVolumeSlotInput> missing = new ArrayList<>();
        for (SlotBound bound : expected) {
            SlotKey key = new SlotKey(bound.start(), bound.end());
            if (targetByKey.containsKey(key)) {
                continue;
            }
            ExerciseVolumeSlotInput seed = archiveByKey.get(key);
            missing.add(ExerciseVolumeSlotInput.create(
                    targetId,
                    bound.start(),
                    bound.end(),
                    seed != null ? seed.getActualVolume() : BigDecimal.ZERO,
                    seed != null ? "ARCHIVE" : "MANUAL",
                    seed != null ? seed.getImportBatchId() : null,
                    actorCcgid,
                    now));
        }
        slotVolumes.deleteAllInBatch(targetRows.stream()
                .filter(row -> !expectedKeys.contains(
                        new SlotKey(row.getSlotStartAt(), row.getSlotEndAt())))
                .toList());
        slotVolumes.saveAll(missing);
    }

    private static void addYearRange(Set<Short> years, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return;
        }
        int start = Math.min(from.getYear(), to.getYear());
        int end = Math.max(from.getYear(), to.getYear());
        for (int year = start; year <= end; year++) {
            years.add((short) year);
        }
    }

    private static short primaryYear(LocalDate sizingMonth) {
        return (short) YearMonth.from(sizingMonth).getYear();
    }

    private static LocalDate slotEnd(RstExercise exercise) {
        return exercise.getSlotStartDate()
                .plusWeeks(exercise.getSlotWeeks())
                .minusDays(1);
    }

    private static String formatYears(Set<Short> years) {
        return years.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    private static ApiException initializationConflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static TeamSetupInput toInput(ExerciseTeamSetup source) {
        return new TeamSetupInput(
                source.getAgentsLt6m(),
                source.getAgents6To24m(),
                source.getAgents24To48m(),
                source.getAgentsGt48m(),
                source.getPaidLeaveDays(),
                source.getOtherLeaveDays(),
                source.getAvailabilityRatio(),
                source.getAutomationRatio(),
                source.getMaxOvertimeMinutes(),
                source.getSlaType(),
                source.getSlaTargetRatio(),
                source.getSlaTurnaroundMinutes(),
                source.getSlaStartTime(),
                source.getSlaEndTime(),
                source.getSlaWeekendEnabled(),
                source.getWeekendShiftHc(),
                source.getSkeletonRatio(),
                source.getWeekendCode());
    }

    private record SlotKey(Instant start, Instant end) {
    }
}
