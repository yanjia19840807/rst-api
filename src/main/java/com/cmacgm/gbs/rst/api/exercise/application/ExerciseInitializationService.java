package com.cmacgm.gbs.rst.api.exercise.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseShift;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup.TeamSetupInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.ProductionSupportItemScope;
import com.cmacgm.gbs.rst.api.associateddata.domain.VolumeDailyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.VolumeMonthlyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.VolumeSlotInput;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseShiftRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ProductionSupportItemScopeRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.VolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.VolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.VolumeSlotInputRepository;
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
    private final ProductionSupportItemRepository supportItems;
    private final ProductionSupportItemScopeRepository supportScopes;
    private final VolumeMonthlyInputRepository monthlyVolumes;
    private final VolumeDailyInputRepository dailyVolumes;
    private final VolumeSlotInputRepository slotVolumes;
    private final ExerciseHolidayRepository holidays;
    private final CycleTimeBaselineRepository cycleTimeBaselines;
    private final HolidayTemplateService holidayTemplates;
    private final Clock clock;

    public ExerciseInitializationService(
            RstExerciseRepository exercises,
            ExerciseTeamSetupRepository teamSetups,
            ExerciseShiftRepository shifts,
            ProductionSupportItemRepository supportItems,
            ProductionSupportItemScopeRepository supportScopes,
            VolumeMonthlyInputRepository monthlyVolumes,
            VolumeDailyInputRepository dailyVolumes,
            VolumeSlotInputRepository slotVolumes,
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
        }
        return notices;
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
        target.replaceInputs(toInput(source), actorUserId, now);
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
        for (ProductionSupportItem item : supportItems
                .findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(source.getId())) {
            ProductionSupportItem copy = ProductionSupportItem.createFromArchive(
                    target.getId(), item, actorUserId, now);
            supportItems.save(copy);
            List<ProductionSupportItemScope> scopes =
                    supportScopes.findByProductionSupportItemId(item.getId());
            List<UUID> mapped = new ArrayList<>();
            for (ProductionSupportItemScope scope : scopes) {
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
                    supportScopes.save(ProductionSupportItemScope.assign(copy.getId(), kpiId, ratio));
                }
            }
        }
    }

    private void copyVolumes(UUID sourceId, RstExercise target, UUID actorUserId, Instant now) {
        LocalDate windowStart = earliest(target.getTmsFrom(), target.getSlotStartDate());
        LocalDate windowEnd = latest(target.getTmsTo(), slotEnd(target));
        Set<String> months = monthsCovered(windowStart, windowEnd);
        months.add(target.getSizingMonth());

        for (VolumeMonthlyInput row : monthlyVolumes.findByExerciseIdOrderByMonthAsc(sourceId)) {
            if (months.contains(row.getMonth())) {
                monthlyVolumes.save(VolumeMonthlyInput.create(
                        target.getId(),
                        row.getMonth(),
                        row.getActualVolume(),
                        row.getCommercialRatio(),
                        row.getManualForecastVolume(),
                        actorUserId,
                        now));
            }
        }
        for (VolumeDailyInput row : dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(sourceId)) {
            LocalDate date = row.getVolumeDate();
            if (date != null && !date.isBefore(windowStart) && !date.isAfter(windowEnd)) {
                dailyVolumes.save(VolumeDailyInput.create(
                        target.getId(),
                        date,
                        row.getActualVolume(),
                        row.getDailyAdjustmentRatio(),
                        row.getManualForecastVolume(),
                        actorUserId,
                        now));
            }
        }
        Instant slotFrom = windowStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant slotTo = windowEnd.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        for (VolumeSlotInput row : slotVolumes.findByExerciseIdOrderBySlotStartAtAsc(sourceId)) {
            if (row.getSlotStartAt() != null
                    && !row.getSlotStartAt().isBefore(slotFrom)
                    && row.getSlotStartAt().isBefore(slotTo)) {
                slotVolumes.save(VolumeSlotInput.create(
                        target.getId(),
                        row.getSlotStartAt(),
                        row.getSlotEndAt(),
                        row.getRawVolume(),
                        row.getTimezone(),
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
