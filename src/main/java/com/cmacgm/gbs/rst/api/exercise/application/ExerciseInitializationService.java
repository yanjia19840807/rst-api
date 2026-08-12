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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.associateddata.application.SupportDerivedRefresher;
import com.cmacgm.gbs.rst.api.associateddata.application.VolumeTrainWindows;
import com.cmacgm.gbs.rst.api.associateddata.application.VolumeTrainWindows.SlotBound;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseCalendar;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup.TeamSetupInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeDailyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeMonthlyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeSlotInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseCalendarRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeSlotInputRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.cycletime.application.SystemCycleTimeBaselineWriter;
import com.cmacgm.gbs.rst.api.cycletime.domain.ExerciseTmsSession;
import com.cmacgm.gbs.rst.api.cycletime.persistence.ExerciseTmsSessionRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.ApplyTemplatesResult;
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

    private static final List<String> ARCHIVE_STATUSES = List.of("VALIDATED", "ARCHIVED");

    private final RstExerciseRepository exercises;
    private final ExerciseTeamSetupRepository teamSetups;
    private final ExerciseCalendarRepository calendars;
    private final ExerciseHolidayRepository holidays;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseVolumeMonthlyInputRepository monthlyVolumes;
    private final ExerciseVolumeDailyInputRepository dailyVolumes;
    private final ExerciseVolumeSlotInputRepository slotVolumes;
    private final TmsSessionRepository tmsSessions;
    private final ExerciseTmsSessionRepository exerciseTmsSessions;
    private final HolidayTemplateService holidayTemplates;
    private final SupportDerivedRefresher supportDerivedRefresher;
    private final SystemCycleTimeBaselineWriter systemCycleTime;
    private final Clock clock;

    public ExerciseInitializationService(
            RstExerciseRepository exercises,
            ExerciseTeamSetupRepository teamSetups,
            ExerciseCalendarRepository calendars,
            ExerciseHolidayRepository holidays,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseVolumeMonthlyInputRepository monthlyVolumes,
            ExerciseVolumeDailyInputRepository dailyVolumes,
            ExerciseVolumeSlotInputRepository slotVolumes,
            TmsSessionRepository tmsSessions,
            ExerciseTmsSessionRepository exerciseTmsSessions,
            HolidayTemplateService holidayTemplates,
            SupportDerivedRefresher supportDerivedRefresher,
            SystemCycleTimeBaselineWriter systemCycleTime,
            Clock clock) {
        this.exercises = exercises;
        this.teamSetups = teamSetups;
        this.calendars = calendars;
        this.holidays = holidays;
        this.supportItems = supportItems;
        this.monthlyVolumes = monthlyVolumes;
        this.dailyVolumes = dailyVolumes;
        this.slotVolumes = slotVolumes;
        this.tmsSessions = tmsSessions;
        this.exerciseTmsSessions = exerciseTmsSessions;
        this.holidayTemplates = holidayTemplates;
        this.supportDerivedRefresher = supportDerivedRefresher;
        this.systemCycleTime = systemCycleTime;
        this.clock = clock;
    }

    /**
     * Initializes Associated Data for a newly created Exercise.
     */
    @Transactional
    public List<String> initialize(RstExercise exercise, UUID actorUserId) {
        Instant now = clock.instant();
        List<String> notices = new ArrayList<>();
        String center = exercise.getToolkitSnapshot() != null
                ? exercise.getToolkitSnapshot().getCenter()
                : null;
        Set<Short> holidayYears = resolveHolidayYears(exercise);
        short primaryYear = primaryYear(exercise.getSizingMonth());

        Optional<RstExercise> archive = findLatestArchive(exercise.getToolkitId());
        if (archive.isPresent()) {
            RstExercise source = archive.get();
            seedFromArchive(source, exercise, actorUserId, now);
            notices.add("Associated Data seeded from archived exercise "
                    + source.getExerciseCode()
                    + " (Team Setup, Production Support, Calendar & Holidays).");
            short archivePrimary = primaryYear(source.getSizingMonth());
            if (archivePrimary != primaryYear) {
                notices.add("Sizing year differs from the archive ("
                        + archivePrimary + " → " + primaryYear
                        + "). Holidays were merged for " + formatYears(holidayYears) + ".");
            }
        } else {
            notices.add(
                    "No validated/archived exercise found for this Toolkit. "
                            + "Associated Data starts empty; Calendar uses Center templates.");
        }

        ApplyTemplatesResult applied = holidayTemplates.applyPublishedTemplates(
                exercise.getId(),
                center,
                primaryYear,
                holidayYears,
                actorUserId,
                false);
        notices.addAll(applied.notices());

        if (archive.isPresent()) {
            copyCalendarHeader(archive.get().getId(), exercise.getId(), actorUserId, now);
            copyCustomHolidays(
                    archive.get().getId(), exercise.getId(), holidayYears, actorUserId, now);
            holidayTemplates.refreshWorkingDaysForExercise(exercise.getId(), actorUserId);
            supportDerivedRefresher.refresh(exercise.getId());
        }

        syncVolumeGrids(exercise, archive.map(RstExercise::getId).orElse(null), actorUserId);
        notices.add("Volume Input grids prepared for Sizing and Slot training windows"
                + (archive.isPresent() ? " (overlapping archive values copied)." : "."));

        notices.add(syncTmsPopulation(exercise, actorUserId));
        return notices;
    }

    /**
     * Reconciles Volume Input grids after Exercise period changes (no archive re-seed).
     */
    @Transactional
    public void ensureTrainVolumeGrids(RstExercise exercise, UUID actorUserId) {
        syncVolumeGrids(exercise, null, actorUserId);
    }

    /**
     * Reconciles Embedded TMS population for the Exercise TMS period and refreshes SYSTEM CT.
     */
    @Transactional
    public String syncTmsPopulation(RstExercise exercise, UUID actorUserId) {
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
                        exerciseId, sessionId, true, null, actorUserId, now));
            }
        }
        if (!missing.isEmpty()) {
            exerciseTmsSessions.saveAll(missing);
        }
        exerciseTmsSessions.flush();

        systemCycleTime.refreshIfSystemOrAbsent(exerciseId, actorUserId);
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
        return exercises.findArchivedByToolkit(
                        toolkitId, ARCHIVE_STATUSES, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    private void seedFromArchive(
            RstExercise source,
            RstExercise target,
            UUID actorUserId,
            Instant now) {
        copyTeamSetup(source.getId(), target.getId(), actorUserId, now);
        copySupport(source, target, actorUserId, now);
        target.markInitializedFrom(source.getId(), actorUserId, now);
    }

    private void copyTeamSetup(UUID sourceId, UUID targetId, UUID actorUserId, Instant now) {
        ExerciseTeamSetup target = teamSetups.findById(targetId)
                .orElseThrow(() -> initializationConflict(
                        "exercise-team-setup-shell-missing",
                        "The target Exercise Team Setup shell is missing."));
        ExerciseTeamSetup source = teamSetups.findById(sourceId)
                .orElseThrow(() -> initializationConflict(
                        "archive-team-setup-missing",
                        "The archived Exercise has no Team Setup to copy."));
        // Cycle Time is not copied from archive; capacity refreshes after SYSTEM baseline.
        target.replaceInputs(toInput(source), null, actorUserId, now);
        if (source.getWorkingDaysPerYear() != null) {
            target.applyCalendarWorkingDays(
                    source.getWorkingDaysPerYear(), null, actorUserId, now);
        }
        teamSetups.save(target);
    }

    private void copySupport(RstExercise source, RstExercise target, UUID actorUserId, Instant now) {
        ExerciseTeamSetup targetSetup = teamSetups.findById(target.getId())
                .orElseThrow(() -> initializationConflict(
                        "exercise-team-setup-shell-missing",
                        "The target Exercise Team Setup shell is missing."));
        BigDecimal workingDays = targetSetup.getWorkingDaysPerYear();
        BigDecimal fteHours = SupportWorkloadMath.fteAnnualHours(targetSetup);
        List<ExerciseProductionSupportItem> copies = supportItems
                .findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(source.getId())
                .stream()
                .map(sourceItem -> {
                    BigDecimal multiplier;
                    try {
                        multiplier = SupportWorkloadMath.annualMultiplier(
                                sourceItem.getFrequencyCode(), workingDays);
                    } catch (IllegalArgumentException ex) {
                        multiplier = sourceItem.getAnnualMultiplier();
                    }
                    return ExerciseProductionSupportItem.createFromArchive(
                            target.getId(),
                            sourceItem,
                            multiplier,
                            fteHours,
                            actorUserId,
                            now);
                })
                .toList();
        supportItems.saveAll(copies);
    }

    private void copyCalendarHeader(
            UUID sourceId, UUID targetId, UUID actorUserId, Instant now) {
        ExerciseCalendar target = calendars.findById(targetId)
                .orElseThrow(() -> initializationConflict(
                        "exercise-calendar-shell-missing",
                        "The target Exercise Calendar shell is missing."));
        calendars.findById(sourceId).ifPresent(source -> {
            target.applyTemplateMeta(
                    source.getWeekendCode(),
                    source.getSourceTemplateId(),
                    source.getSourceTemplateVersion(),
                    source.getBaselineYear(),
                    source.getBaselineSource(),
                    source.getBaselineVersion(),
                    actorUserId,
                    now);
            if (source.getWorkingDaysPerYear() != null) {
                target.setWorkingDaysPerYear(source.getWorkingDaysPerYear());
            }
            calendars.save(target);
        });
    }

    private void copyCustomHolidays(
            UUID sourceId,
            UUID targetId,
            Set<Short> holidayYears,
            UUID actorUserId,
            Instant now) {
        Set<String> existingKeys = holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(targetId)
                .stream()
                .map(ExerciseInitializationService::holidayKey)
                .collect(Collectors.toSet());
        List<ExerciseHoliday> copies = new ArrayList<>();
        for (ExerciseHoliday holiday : holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(sourceId)) {
            LocalDate date = holiday.getHolidayDate();
            if (!"CUSTOM".equalsIgnoreCase(holiday.getHolidayType())
                    || date == null
                    || !holidayYears.contains((short) date.getYear())) {
                continue;
            }
            String key = holidayKey(holiday);
            if (existingKeys.add(key)) {
                copies.add(ExerciseHoliday.create(
                        targetId,
                        date,
                        holiday.getHolidayName(),
                        "CUSTOM",
                        holiday.getWorkingDayOverride(),
                        actorUserId,
                        now));
            }
        }
        holidays.saveAll(copies);
    }

    private void syncVolumeGrids(RstExercise exercise, UUID archiveExerciseId, UUID actorUserId) {
        Instant now = clock.instant();
        syncMonthly(exercise, archiveExerciseId, actorUserId, now);
        syncDaily(exercise, archiveExerciseId, actorUserId, now);
        syncSlot(exercise, archiveExerciseId, actorUserId, now);
    }

    private void syncMonthly(
            RstExercise exercise, UUID archiveExerciseId, UUID actorUserId, Instant now) {
        UUID targetId = exercise.getId();
        List<LocalDate> expected = VolumeTrainWindows.monthlyTrainMonths(exercise.getSizingMonth());
        Set<LocalDate> expectedKeys = new HashSet<>(expected);
        List<ExerciseVolumeMonthlyInput> targetRows =
                monthlyVolumes.findByExerciseIdOrderByMonthAsc(targetId);
        Map<LocalDate, ExerciseVolumeMonthlyInput> targetByKey = new HashMap<>();
        targetRows.forEach(row -> targetByKey.put(row.getMonth(), row));
        Map<LocalDate, ExerciseVolumeMonthlyInput> archiveByKey = new HashMap<>();
        if (archiveExerciseId != null) {
            monthlyVolumes.findByExerciseIdOrderByMonthAsc(archiveExerciseId)
                    .forEach(row -> archiveByKey.put(row.getMonth(), row));
        }

        List<ExerciseVolumeMonthlyInput> missing = new ArrayList<>();
        for (LocalDate month : expected) {
            if (targetByKey.containsKey(month)) {
                continue;
            }
            ExerciseVolumeMonthlyInput seed = archiveByKey.get(month);
            missing.add(ExerciseVolumeMonthlyInput.create(
                    targetId,
                    month,
                    seed != null ? seed.getActualVolume() : null,
                    seed != null ? "ARCHIVE" : "MANUAL",
                    seed != null ? seed.getImportBatchId() : null,
                    actorUserId,
                    now));
        }
        monthlyVolumes.deleteAllInBatch(targetRows.stream()
                .filter(row -> !expectedKeys.contains(row.getMonth()))
                .toList());
        monthlyVolumes.saveAll(missing);
    }

    private void syncDaily(
            RstExercise exercise, UUID archiveExerciseId, UUID actorUserId, Instant now) {
        UUID targetId = exercise.getId();
        List<LocalDate> expected = VolumeTrainWindows.dailyTrainDates(exercise.getSizingMonth());
        Set<LocalDate> expectedKeys = new HashSet<>(expected);
        List<ExerciseVolumeDailyInput> targetRows =
                dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(targetId);
        Map<LocalDate, ExerciseVolumeDailyInput> targetByKey = new HashMap<>();
        targetRows.forEach(row -> targetByKey.put(row.getVolumeDate(), row));
        Map<LocalDate, ExerciseVolumeDailyInput> archiveByKey = new HashMap<>();
        if (archiveExerciseId != null) {
            dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(archiveExerciseId)
                    .forEach(row -> archiveByKey.put(row.getVolumeDate(), row));
        }

        List<ExerciseVolumeDailyInput> missing = new ArrayList<>();
        for (LocalDate date : expected) {
            if (targetByKey.containsKey(date)) {
                continue;
            }
            ExerciseVolumeDailyInput seed = archiveByKey.get(date);
            missing.add(ExerciseVolumeDailyInput.create(
                    targetId,
                    date,
                    seed != null ? seed.getActualVolume() : null,
                    seed != null ? "ARCHIVE" : "MANUAL",
                    seed != null ? seed.getImportBatchId() : null,
                    actorUserId,
                    now));
        }
        dailyVolumes.deleteAllInBatch(targetRows.stream()
                .filter(row -> !expectedKeys.contains(row.getVolumeDate()))
                .toList());
        dailyVolumes.saveAll(missing);
    }

    private void syncSlot(
            RstExercise exercise, UUID archiveExerciseId, UUID actorUserId, Instant now) {
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
                    actorUserId,
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

    private static String holidayKey(ExerciseHoliday holiday) {
        return holiday.getHolidayDate()
                + "|"
                + holiday.getHolidayName().toLowerCase(Locale.ROOT);
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
                source.getDeliveryHc(),
                source.getWorkingHoursPerDay(),
                source.getPaidLeaveDays(),
                source.getOtherLeaveDays(),
                source.getWeekendCode(),
                source.getAvailabilityRatio(),
                source.getAutomationRatio(),
                source.getCapacityRatio(),
                source.getMaxOvertimeMinutes(),
                source.getSlaType(),
                source.getSlaTargetRatio(),
                source.getSlaTurnaroundMinutes(),
                source.getSlaStartTime(),
                source.getSlaEndTime(),
                source.getSlaWeekendEnabled(),
                source.getWeekendShiftHc(),
                source.getSkeletonRatio());
    }

    private record SlotKey(Instant start, Instant end) {
    }
}
