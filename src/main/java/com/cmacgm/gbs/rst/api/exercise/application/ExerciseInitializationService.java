package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.associateddata.application.VolumeTrainWindows;
import com.cmacgm.gbs.rst.api.associateddata.application.VolumeTrainWindows.SlotBound;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseShift;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup.TeamSetupInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItemScope;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeDailyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeMonthlyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeSlotInput;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseShiftRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemScopeRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeSlotInputRepository;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.ApplyTemplatesResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds Associated Data when an Exercise is created:
 * archive-first copy (Team Setup / Support / Shifts / Volume slice / manual Cycle Time),
 * then multi-year Center holiday templates with Working Days on the sizing primary year.
 */
@Service
public class ExerciseInitializationService {

    private static final List<String> ARCHIVE_STATUSES = List.of("VALIDATED", "ARCHIVED");

    private final RstExerciseRepository exercises;
    private final ExerciseTeamSetupRepository teamSetups;
    private final ExerciseShiftRepository shifts;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseProductionSupportItemScopeRepository supportScopes;
    private final ExerciseVolumeMonthlyInputRepository monthlyVolumes;
    private final ExerciseVolumeDailyInputRepository dailyVolumes;
    private final ExerciseVolumeSlotInputRepository slotVolumes;
    private final ExerciseHolidayRepository holidays;
    private final CycleTimeBaselineRepository cycleTimeBaselines;
    private final HolidayTemplateService holidayTemplates;
    private final Clock clock;

    public ExerciseInitializationService(
            RstExerciseRepository exercises,
            ExerciseTeamSetupRepository teamSetups,
            ExerciseShiftRepository shifts,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseProductionSupportItemScopeRepository supportScopes,
            ExerciseVolumeMonthlyInputRepository monthlyVolumes,
            ExerciseVolumeDailyInputRepository dailyVolumes,
            ExerciseVolumeSlotInputRepository slotVolumes,
            ExerciseHolidayRepository holidays,
            CycleTimeBaselineRepository cycleTimeBaselines,
            HolidayTemplateService holidayTemplates,
            Clock clock) {
        this.exercises = exercises;
        this.teamSetups = teamSetups;
        this.shifts = shifts;
        this.supportItems = supportItems;
        this.supportScopes = supportScopes;
        this.monthlyVolumes = monthlyVolumes;
        this.dailyVolumes = dailyVolumes;
        this.slotVolumes = slotVolumes;
        this.holidays = holidays;
        this.cycleTimeBaselines = cycleTimeBaselines;
        this.holidayTemplates = holidayTemplates;
        this.clock = clock;
    }

    /**
     * Initializes AD for a newly created Exercise. Caller must already persist empty Team Setup
     * and Calendar shells.
     *
     * @return human-readable notices for the Supervisor (cross-year, missing templates, etc.)
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
            copyTeamSetup(source.getId(), exercise.getId(), actorUserId, now);
            copyShifts(source.getId(), exercise.getId(), actorUserId, now);
            copySupport(source, exercise, actorUserId, now);
            copyVolumes(source.getId(), exercise, actorUserId, now);
            copyManualCycleTime(source.getId(), exercise.getId(), actorUserId, now);
            exercise.markInitializedFrom(source.getId(), actorUserId, now);
            exercises.save(exercise);
            notices.add("Associated Data initialized from archived exercise " + source.getExerciseCode() + ".");
            notices.add(
                    "Volume seeded from "
                            + source.getExerciseCode()
                            + " for overlapping training periods.");
            short archivePrimary = primaryYear(source.getSizingMonth());
            if (archivePrimary != primaryYear) {
                notices.add(
                        "Sizing year differs from the archive ("
                                + archivePrimary
                                + " → "
                                + primaryYear
                                + "). Working Days use "
                                + primaryYear
                                + "; holidays were merged for "
                                + formatYears(holidayYears)
                                + ".");
            }
        } else {
            notices.add("No validated/archived exercise found for this Toolkit. Calendar seeded from Center templates.");
        }

        ensureTrainVolumeGrids(exercise, actorUserId);
        notices.add("Volume Input rows generated for training windows (monthly / daily / per-slot).");

        ApplyTemplatesResult applied = holidayTemplates.applyPublishedTemplates(
                exercise.getId(),
                center,
                primaryYear,
                holidayYears,
                actorUserId,
                false);
        notices.addAll(applied.notices());

        if (archive.isPresent()) {
            copyCustomHolidays(archive.get().getId(), exercise.getId(), holidayYears, actorUserId, now);
            holidayTemplates.refreshWorkingDaysForExercise(exercise.getId(), actorUserId);
            refreshSupportDerived(exercise.getId());
        }
        return notices;
    }

    private void refreshSupportDerived(UUID exerciseId) {
        ExerciseTeamSetup setup = teamSetups.findById(exerciseId).orElse(null);
        BigDecimal workingDays = setup != null ? setup.getWorkingDaysPerYear() : null;
        BigDecimal fteHours = SupportWorkloadMath.fteAnnualHours(setup);
        for (ExerciseProductionSupportItem item : supportItems
                .findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(exerciseId)) {
            try {
                BigDecimal multiplier = SupportWorkloadMath.annualMultiplier(
                        item.getFrequencyCode(), workingDays);
                item.applyDerived(multiplier, fteHours);
                supportItems.save(item);
            } catch (IllegalArgumentException ignored) {
                // Keep historical rows with unrecognized frequency codes.
            }
        }
    }

    private Optional<RstExercise> findLatestArchive(UUID toolkitId) {
        List<RstExercise> rows = exercises.findArchivedByToolkit(
                toolkitId, ARCHIVE_STATUSES, PageRequest.of(0, 1));
        return rows.stream().findFirst();
    }

    private void copyTeamSetup(UUID sourceId, UUID targetId, UUID actorUserId, Instant now) {
        ExerciseTeamSetup target = teamSetups.findById(targetId).orElse(null);
        ExerciseTeamSetup source = teamSetups.findById(sourceId).orElse(null);
        if (target == null || source == null) {
            return;
        }
        BigDecimal cycleTimeSeconds = cycleTimeBaselines
                .findByExerciseIdAndActiveTrue(sourceId)
                .map(baseline -> baseline.getMedianSeconds())
                .orElse(null);
        target.replaceInputs(toInput(source), cycleTimeSeconds, actorUserId, now);
        if (source.getWorkingDaysPerYear() != null) {
            target.applyCalendarWorkingDays(
                    source.getWorkingDaysPerYear(), cycleTimeSeconds, actorUserId, now);
        }
        teamSetups.save(target);
    }

    private void copyShifts(UUID sourceId, UUID targetId, UUID actorUserId, Instant now) {
        for (ExerciseShift shift : shifts.findByExerciseIdAndDeletedAtIsNullOrderByShiftNoAsc(sourceId)) {
            shifts.save(ExerciseShift.create(
                    targetId,
                    shift.getShiftNo(),
                    shift.getStartTime(),
                    shift.getDurationMinutes(),
                    shift.getHeadcount(),
                    shift.isWorksOnWeekend(),
                    actorUserId,
                    now));
        }
    }

    private void copySupport(RstExercise source, RstExercise target, UUID actorUserId, Instant now) {
        Map<String, UUID> sourceKpiKeys = kpiKeyMap(source);
        Map<String, UUID> targetKpiKeys = kpiKeyMap(target);
        ExerciseTeamSetup targetSetup = teamSetups.findById(target.getId()).orElse(null);
        BigDecimal workingDays = targetSetup != null ? targetSetup.getWorkingDaysPerYear() : null;
        BigDecimal fteHours = SupportWorkloadMath.fteAnnualHours(targetSetup);
        for (ExerciseProductionSupportItem item : supportItems
                .findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(source.getId())) {
            BigDecimal multiplier;
            try {
                multiplier = SupportWorkloadMath.annualMultiplier(item.getFrequencyCode(), workingDays);
            } catch (IllegalArgumentException ex) {
                multiplier = item.getAnnualMultiplier();
            }
            ExerciseProductionSupportItem copy = ExerciseProductionSupportItem.createFromArchive(
                    target.getId(), item, multiplier, fteHours, actorUserId, now);
            supportItems.save(copy);
            List<ExerciseProductionSupportItemScope> scopes =
                    supportScopes.findByExerciseProductionSupportItemId(item.getId());
            List<UUID> mapped = new ArrayList<>();
            for (ExerciseProductionSupportItemScope scope : scopes) {
                String key = reverseLookup(sourceKpiKeys, scope.getExerciseSharedKpiLineId());
                if (key != null && targetKpiKeys.containsKey(key)) {
                    mapped.add(targetKpiKeys.get(key));
                }
            }
            if (mapped.isEmpty()) {
                mapped.addAll(targetKpiKeys.values());
            }
            if (!mapped.isEmpty()) {
                BigDecimal ratio = BigDecimal.ONE.divide(
                        BigDecimal.valueOf(mapped.size()), 8, java.math.RoundingMode.HALF_UP);
                for (UUID kpiId : mapped) {
                    supportScopes.save(ExerciseProductionSupportItemScope.assign(copy.getId(), kpiId, ratio));
                }
            }
        }
    }

    /**
     * Rebuilds Volume Input grids to the Exercise training windows, keeping values for overlapping keys.
     */
    @Transactional
    public void ensureTrainVolumeGrids(RstExercise exercise, UUID actorUserId) {
        Instant now = clock.instant();
        UUID exerciseId = exercise.getId();
        syncMonthlyTrainGrid(exercise, actorUserId, now);
        syncDailyTrainGrid(exercise, actorUserId, now);
        syncSlotTrainGrid(exercise, actorUserId, now);
    }

    private void syncMonthlyTrainGrid(RstExercise exercise, UUID actorUserId, Instant now) {
        UUID exerciseId = exercise.getId();
        List<String> expected = VolumeTrainWindows.monthlyTrainMonths(exercise.getSizingMonth());
        Map<String, ExerciseVolumeMonthlyInput> existing = new HashMap<>();
        for (ExerciseVolumeMonthlyInput row : monthlyVolumes.findByExerciseIdOrderByMonthAsc(exerciseId)) {
            existing.put(row.getMonth(), row);
        }
        monthlyVolumes.deleteByExerciseId(exerciseId);
        monthlyVolumes.flush();
        for (String month : expected) {
            ExerciseVolumeMonthlyInput prior = existing.get(month);
            if (prior != null) {
                monthlyVolumes.save(ExerciseVolumeMonthlyInput.create(
                        exerciseId,
                        month,
                        prior.getActualVolume(),
                        prior.getCommercialRatio(),
                        prior.getManualForecastVolume(),
                        prior.getSourceType(),
                        null,
                        actorUserId,
                        now));
            } else {
                monthlyVolumes.save(ExerciseVolumeMonthlyInput.create(
                        exerciseId, month, null, null, null, "MANUAL", null, actorUserId, now));
            }
        }
    }

    private void syncDailyTrainGrid(RstExercise exercise, UUID actorUserId, Instant now) {
        UUID exerciseId = exercise.getId();
        List<LocalDate> expected = VolumeTrainWindows.dailyTrainDates(exercise.getSizingMonth());
        Map<LocalDate, ExerciseVolumeDailyInput> existing = new HashMap<>();
        for (ExerciseVolumeDailyInput row : dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(exerciseId)) {
            existing.put(row.getVolumeDate(), row);
        }
        dailyVolumes.deleteByExerciseId(exerciseId);
        dailyVolumes.flush();
        for (LocalDate date : expected) {
            ExerciseVolumeDailyInput prior = existing.get(date);
            if (prior != null) {
                dailyVolumes.save(ExerciseVolumeDailyInput.create(
                        exerciseId,
                        date,
                        prior.getActualVolume(),
                        prior.getDailyAdjustmentRatio(),
                        prior.getManualForecastVolume(),
                        prior.getSourceType(),
                        null,
                        actorUserId,
                        now));
            } else {
                dailyVolumes.save(ExerciseVolumeDailyInput.create(
                        exerciseId, date, null, null, null, "MANUAL", null, actorUserId, now));
            }
        }
    }

    private void syncSlotTrainGrid(RstExercise exercise, UUID actorUserId, Instant now) {
        UUID exerciseId = exercise.getId();
        List<SlotBound> expected = VolumeTrainWindows.slotTrainBounds(
                exercise.getSlotStartDate(), exercise.getSlotWeeks());
        Map<String, ExerciseVolumeSlotInput> existing = new HashMap<>();
        for (ExerciseVolumeSlotInput row : slotVolumes.findByExerciseIdOrderBySlotStartAtAsc(exerciseId)) {
            existing.put(row.getSlotStartAt() + "|" + row.getSlotEndAt(), row);
        }
        slotVolumes.deleteByExerciseId(exerciseId);
        slotVolumes.flush();
        for (SlotBound bound : expected) {
            String key = bound.start() + "|" + bound.end();
            ExerciseVolumeSlotInput prior = existing.get(key);
            if (prior != null) {
                slotVolumes.save(ExerciseVolumeSlotInput.create(
                        exerciseId,
                        bound.start(),
                        bound.end(),
                        prior.getRawVolume(),
                        prior.getTimezone(),
                        prior.getSourceType(),
                        null,
                        actorUserId,
                        now));
            } else {
                slotVolumes.save(ExerciseVolumeSlotInput.create(
                        exerciseId,
                        bound.start(),
                        bound.end(),
                        BigDecimal.ZERO,
                        VolumeTrainWindows.DEFAULT_SLOT_TIMEZONE,
                        "MANUAL",
                        null,
                        actorUserId,
                        now));
            }
        }
    }

    private void copyVolumes(UUID sourceId, RstExercise target, UUID actorUserId, Instant now) {
        Set<String> months = VolumeTrainWindows.monthlyTrainMonthSet(target.getSizingMonth());
        Set<LocalDate> dates = new LinkedHashSet<>(VolumeTrainWindows.dailyTrainDates(target.getSizingMonth()));
        LocalDate slotStart = target.getSlotStartDate();
        LocalDate slotEndDate = VolumeTrainWindows.slotTrainEnd(slotStart, target.getSlotWeeks());
        Instant slotFrom = slotStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant slotTo = slotEndDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        for (ExerciseVolumeMonthlyInput row : monthlyVolumes.findByExerciseIdOrderByMonthAsc(sourceId)) {
            if (months.contains(row.getMonth())) {
                monthlyVolumes.save(ExerciseVolumeMonthlyInput.create(
                        target.getId(),
                        row.getMonth(),
                        row.getActualVolume(),
                        row.getCommercialRatio(),
                        row.getManualForecastVolume(),
                        "ARCHIVE",
                        null,
                        actorUserId,
                        now));
            }
        }
        for (ExerciseVolumeDailyInput row : dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(sourceId)) {
            LocalDate date = row.getVolumeDate();
            if (date != null && dates.contains(date)) {
                dailyVolumes.save(ExerciseVolumeDailyInput.create(
                        target.getId(),
                        date,
                        row.getActualVolume(),
                        row.getDailyAdjustmentRatio(),
                        row.getManualForecastVolume(),
                        "ARCHIVE",
                        null,
                        actorUserId,
                        now));
            }
        }
        for (ExerciseVolumeSlotInput row : slotVolumes.findByExerciseIdOrderBySlotStartAtAsc(sourceId)) {
            if (row.getSlotStartAt() != null
                    && !row.getSlotStartAt().isBefore(slotFrom)
                    && row.getSlotStartAt().isBefore(slotTo)) {
                slotVolumes.save(ExerciseVolumeSlotInput.create(
                        target.getId(),
                        row.getSlotStartAt(),
                        row.getSlotEndAt(),
                        row.getRawVolume(),
                        row.getTimezone(),
                        "ARCHIVE",
                        null,
                        actorUserId,
                        now));
            }
        }
    }

    private void copyManualCycleTime(UUID sourceId, UUID targetId, UUID actorUserId, Instant now) {
        cycleTimeBaselines.findByExerciseIdAndActiveTrue(sourceId).ifPresent(baseline -> {
            if (!"MANUAL".equalsIgnoreCase(baseline.getBaselineType())) {
                return;
            }
            cycleTimeBaselines.save(CycleTimeBaseline.createManual(
                    targetId,
                    baseline.getMedianSeconds(),
                    baseline.getManualReason() != null
                            ? baseline.getManualReason()
                            : "Copied from archived exercise",
                    actorUserId,
                    now));
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
                .map(h -> h.getHolidayDate() + "|" + h.getHolidayName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (ExerciseHoliday holiday : holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(sourceId)) {
            if (!"CUSTOM".equalsIgnoreCase(holiday.getHolidayType())) {
                continue;
            }
            LocalDate date = holiday.getHolidayDate();
            if (date == null || !holidayYears.contains((short) date.getYear())) {
                continue;
            }
            String key = date + "|" + holiday.getHolidayName().toLowerCase(Locale.ROOT);
            if (existingKeys.contains(key)) {
                continue;
            }
            holidays.save(ExerciseHoliday.create(
                    targetId,
                    date,
                    holiday.getHolidayName(),
                    "CUSTOM",
                    null,
                    actorUserId,
                    now));
            existingKeys.add(key);
        }
    }

    public static Set<Short> resolveHolidayYears(RstExercise exercise) {
        Set<Short> years = new LinkedHashSet<>();
        years.add(primaryYear(exercise.getSizingMonth()));
        addYearRange(years, exercise.getTmsFrom(), exercise.getTmsTo());
        addYearRange(years, exercise.getSlotStartDate(), slotEnd(exercise));
        return years;
    }

    private static void addYearRange(Set<Short> years, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return;
        }
        int start = Math.min(from.getYear(), to.getYear());
        int end = Math.max(from.getYear(), to.getYear());
        for (int y = start; y <= end; y++) {
            years.add((short) y);
        }
    }

    private static short primaryYear(String sizingMonth) {
        return Short.parseShort(sizingMonth.substring(0, 4));
    }

    private static LocalDate slotEnd(RstExercise exercise) {
        return exercise.getSlotStartDate().plusWeeks(exercise.getSlotWeeks()).minusDays(1);
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate latest(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static Set<String> monthsCovered(LocalDate from, LocalDate to) {
        Set<String> months = new LinkedHashSet<>();
        LocalDate cursor = from.withDayOfMonth(1);
        LocalDate end = to.withDayOfMonth(1);
        while (!cursor.isAfter(end)) {
            months.add(String.format(Locale.ROOT, "%04d-%02d", cursor.getYear(), cursor.getMonthValue()));
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private static String formatYears(Set<Short> years) {
        return years.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static Map<String, UUID> kpiKeyMap(RstExercise exercise) {
        return exercise.getSharedKpiLines().stream()
                .collect(Collectors.toMap(
                        ExerciseInitializationService::kpiKey,
                        ExerciseSharedKpiLine::getId,
                        (a, b) -> a));
    }

    private static String kpiKey(ExerciseSharedKpiLine line) {
        return Objects.toString(line.getCarrier(), "")
                + "|"
                + Objects.toString(line.getSite(), "")
                + "|"
                + Objects.toString(line.getCustomerCountry(), "");
    }

    private static String reverseLookup(Map<String, UUID> map, UUID id) {
        for (Map.Entry<String, UUID> entry : map.entrySet()) {
            if (Objects.equals(entry.getValue(), id)) {
                return entry.getKey();
            }
        }
        return null;
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
}
