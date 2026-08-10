package com.cmacgm.gbs.rst.api.associateddata.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseCalendar;
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
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseCalendarRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseShiftRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemScopeRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeSlotInputRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseInitializationService;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.ApplyTemplatesResult;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.TemplateUpdateHint;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Associated Data CRUD for Supervisor Exercise Team Setup, Shift, Support, Calendar and Volume.
 */
@Service
public class AssociatedDataService {

    private final ExerciseService exercises;
    private final ExerciseTeamSetupRepository teamSetups;
    private final ExerciseShiftRepository shifts;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseProductionSupportItemScopeRepository supportScopes;
    private final ExerciseCalendarRepository calendars;
    private final ExerciseHolidayRepository holidays;
    private final ExerciseVolumeMonthlyInputRepository monthlyVolumes;
    private final ExerciseVolumeDailyInputRepository dailyVolumes;
    private final ExerciseVolumeSlotInputRepository slotVolumes;
    private final HolidayTemplateService holidayTemplates;
    private final CycleTimeBaselineRepository cycleTimeBaselines;
    private final Clock clock;

    /**
     * Creates the Associated Data service.
     */
    public AssociatedDataService(
            ExerciseService exercises,
            ExerciseTeamSetupRepository teamSetups,
            ExerciseShiftRepository shifts,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseProductionSupportItemScopeRepository supportScopes,
            ExerciseCalendarRepository calendars,
            ExerciseHolidayRepository holidays,
            ExerciseVolumeMonthlyInputRepository monthlyVolumes,
            ExerciseVolumeDailyInputRepository dailyVolumes,
            ExerciseVolumeSlotInputRepository slotVolumes,
            HolidayTemplateService holidayTemplates,
            CycleTimeBaselineRepository cycleTimeBaselines,
            Clock clock) {
        this.exercises = exercises;
        this.teamSetups = teamSetups;
        this.shifts = shifts;
        this.supportItems = supportItems;
        this.supportScopes = supportScopes;
        this.calendars = calendars;
        this.holidays = holidays;
        this.monthlyVolumes = monthlyVolumes;
        this.dailyVolumes = dailyVolumes;
        this.slotVolumes = slotVolumes;
        this.holidayTemplates = holidayTemplates;
        this.cycleTimeBaselines = cycleTimeBaselines;
        this.clock = clock;
    }

    /**
     * Returns Team Setup for an Exercise.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return Team Setup view
     */
    @Transactional(readOnly = true)
    public TeamSetupView getTeamSetup(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
        return toTeamSetup(requireTeamSetup(exerciseId));
    }

    /**
     * Replaces Team Setup inputs and recalculates derived fields.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request Team Setup payload
     * @return updated Team Setup view
     */
    @Transactional
    public TeamSetupView putTeamSetup(UUID ownerId, UUID exerciseId, TeamSetupRequest request) {
        RstExercise exercise = editable(ownerId, exerciseId);
        ExerciseTeamSetup setup = requireTeamSetup(exercise.getId());
        BigDecimal calendarWorkingDays = calendars.findById(exerciseId)
                .map(ExerciseCalendar::getWorkingDaysPerYear)
                .orElse(null);
        BigDecimal cycleTimeSeconds = activeCycleTimeSeconds(exerciseId);
        Instant now = clock.instant();
        setup.replaceInputs(request.toInput(), cycleTimeSeconds, ownerId, now);
        if (calendarWorkingDays != null) {
            setup.applyCalendarWorkingDays(
                    calendarWorkingDays, cycleTimeSeconds, ownerId, now);
        }
        TeamSetupView view = toTeamSetup(teamSetups.save(setup));
        refreshSupportDerived(exerciseId);
        return view;
    }

    /**
     * Lists active shifts.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return shifts
     */
    @Transactional(readOnly = true)
    public List<ShiftView> getShifts(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
        return shifts.findByExerciseIdAndDeletedAtIsNullOrderByShiftNoAsc(exerciseId).stream()
                .map(this::toShift)
                .toList();
    }

    /**
     * Replaces the full active shift list for an Exercise.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request shift list
     * @return new active shifts
     */
    @Transactional
    public List<ShiftView> putShifts(UUID ownerId, UUID exerciseId, List<ShiftRequest> request) {
        RstExercise exercise = editable(ownerId, exerciseId);
        Instant now = clock.instant();
        for (ExerciseShift existing : shifts.findByExerciseIdAndDeletedAtIsNullOrderByShiftNoAsc(exerciseId)) {
            existing.softDelete(ownerId, now);
        }
        for (ShiftRequest item : request) {
            shifts.save(ExerciseShift.create(
                    exercise.getId(), item.shiftNo(), item.startTime(), item.durationMinutes(),
                    item.headcount(), item.worksOnWeekend(), ownerId, now));
        }
        return getShifts(ownerId, exerciseId);
    }

    /**
     * Lists active production support items with scopes.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return support items
     */
    @Transactional(readOnly = true)
    public List<SupportItemView> listSupport(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        return supportItems.findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(exercise.getId())
                .stream()
                .map(this::toSupport)
                .toList();
    }

    /**
     * Creates a support item and allocates scopes evenly across KPI lines when none provided.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request support payload
     * @return created item
     */
    @Transactional
    public SupportItemView createSupport(UUID ownerId, UUID exerciseId, SupportItemRequest request) {
        RstExercise exercise = editable(ownerId, exerciseId);
        Instant now = clock.instant();
        ExerciseTeamSetup setup = requireTeamSetup(exerciseId);
        BigDecimal multiplier;
        try {
            multiplier = SupportWorkloadMath.annualMultiplier(
                    request.frequencyCode(), setup.getWorkingDaysPerYear());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "invalid-frequency", ex.getMessage());
        }
        ExerciseProductionSupportItem item = ExerciseProductionSupportItem.create(
                exercise.getId(), request.category(), request.activity(), request.frequencyCode(),
                request.volume(), request.unitOfMeasure(), request.workloadPerUnitMinutes(),
                multiplier, request.comments(), ownerId, now);
        item.applyDerived(multiplier, SupportWorkloadMath.fteAnnualHours(setup));
        supportItems.save(item);
        replaceScopes(item.getId(), resolveKpiIds(exercise, request.kpiLineIds()));
        return toSupport(item);
    }

    /**
     * Updates a support item and replaces scopes.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param itemId support item id
     * @param request support payload
     * @return updated item
     */
    @Transactional
    public SupportItemView updateSupport(
            UUID ownerId, UUID exerciseId, UUID itemId, SupportItemRequest request) {
        RstExercise exercise = editable(ownerId, exerciseId);
        ExerciseProductionSupportItem item = supportItems.findByIdAndExerciseIdAndDeletedAtIsNull(itemId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "support-item-not-found", "The support item was not found."));
        Instant now = clock.instant();
        ExerciseTeamSetup setup = requireTeamSetup(exerciseId);
        BigDecimal multiplier;
        try {
            multiplier = SupportWorkloadMath.annualMultiplier(
                    request.frequencyCode(), setup.getWorkingDaysPerYear());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "invalid-frequency", ex.getMessage());
        }
        item.update(
                request.category(), request.activity(), request.frequencyCode(), request.volume(),
                request.unitOfMeasure(), request.workloadPerUnitMinutes(), multiplier,
                request.comments(), ownerId, now);
        item.applyDerived(multiplier, SupportWorkloadMath.fteAnnualHours(setup));
        supportItems.save(item);
        replaceScopes(item.getId(), resolveKpiIds(exercise, request.kpiLineIds()));
        return toSupport(item);
    }

    /**
     * Soft-deletes a support item.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param itemId support item id
     */
    @Transactional
    public void deleteSupport(UUID ownerId, UUID exerciseId, UUID itemId) {
        editable(ownerId, exerciseId);
        ExerciseProductionSupportItem item = supportItems.findByIdAndExerciseIdAndDeletedAtIsNull(itemId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "support-item-not-found", "The support item was not found."));
        item.softDelete(ownerId, clock.instant());
        supportItems.save(item);
    }

    /**
     * Returns calendar header and active holidays.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return calendar view
     */
    @Transactional(readOnly = true)
    public CalendarView getCalendar(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        ExerciseCalendar calendar = requireCalendar(exerciseId);
        List<HolidayView> holidayViews = holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId)
                .stream()
                .map(this::toHoliday)
                .toList();
        String center = exercise.getToolkitSnapshot() != null
                ? exercise.getToolkitSnapshot().getCenter()
                : null;
        boolean editable = exercise.canEdit();
        TemplateUpdateHint update = editable
                ? holidayTemplates.findTemplateUpdate(exerciseId, center).orElse(null)
                : null;
        return new CalendarView(
                calendar.getWeekendCode(),
                calendar.getBaselineSource(), calendar.getBaselineVersion(),
                calendar.getSourceTemplateId(), calendar.getSourceTemplateVersion(),
                calendar.getBaselineYear(), calendar.getWorkingDaysPerYear(),
                calendar.getVersion(), holidayViews,
                update != null,
                update != null ? update.publishedVersion() : null,
                update != null ? update.message() : null);
    }

    /**
     * Replaces calendar header and full holiday list.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request calendar payload
     * @return updated calendar
     */
    @Transactional
    public CalendarView putCalendar(UUID ownerId, UUID exerciseId, CalendarRequest request) {
        editable(ownerId, exerciseId);
        Instant now = clock.instant();
        ExerciseCalendar calendar = requireCalendar(exerciseId);
        calendar.replace(
                request.weekendCode(),
                request.baselineSource(), request.baselineVersion(), ownerId, now);
        calendars.save(calendar);
        for (ExerciseHoliday existing : holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId)) {
            existing.softDelete(ownerId, now);
            holidays.save(existing);
        }
        holidays.flush();
        if (request.holidays() != null) {
            for (HolidayRequest holiday : request.holidays()) {
                holidays.save(ExerciseHoliday.create(
                        exerciseId, holiday.holidayDate(), holiday.holidayName(),
                        holiday.holidayType(), null, ownerId, now));
            }
        }
        holidayTemplates.refreshWorkingDaysForExercise(exerciseId, ownerId);
        refreshSupportDerived(exerciseId);
        return getCalendar(ownerId, exerciseId);
    }

    /**
     * Re-applies the published Center holiday template for the Exercise sizing year.
     * Preserves CUSTOM holidays.
     */
    @Transactional
    public ReapplyCalendarResult reapplyHolidayTemplate(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = editable(ownerId, exerciseId);
        String center = exercise.getToolkitSnapshot() != null
                ? exercise.getToolkitSnapshot().getCenter()
                : null;
        short primaryYear = Short.parseShort(exercise.getSizingMonth().substring(0, 4));
        ApplyTemplatesResult applied = holidayTemplates.applyPublishedTemplates(
                exerciseId,
                center,
                primaryYear,
                ExerciseInitializationService.resolveHolidayYears(exercise),
                ownerId,
                true);
        refreshSupportDerived(exerciseId);
        return new ReapplyCalendarResult(getCalendar(ownerId, exerciseId), applied.notices());
    }

    /**
     * Lists monthly volumes.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return monthly volumes
     */
    @Transactional(readOnly = true)
    public List<MonthlyVolumeView> getMonthlyVolumes(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
        return monthlyVolumes.findByExerciseIdOrderByMonthAsc(exerciseId).stream()
                .map(v -> new MonthlyVolumeView(
                        v.getId(), v.getMonth(), v.getActualVolume(), v.getCommercialRatio(),
                        v.getManualForecastVolume(), v.getSourceType()))
                .toList();
    }

    /**
     * Replaces the full monthly volume list.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request monthly rows
     * @return replaced list
     */
    @Transactional
    public List<MonthlyVolumeView> putMonthlyVolumes(
            UUID ownerId, UUID exerciseId, List<MonthlyVolumeRequest> request) {
        editable(ownerId, exerciseId);
        Instant now = clock.instant();
        monthlyVolumes.deleteByExerciseId(exerciseId);
        monthlyVolumes.flush();
        for (MonthlyVolumeRequest row : request) {
            monthlyVolumes.save(ExerciseVolumeMonthlyInput.create(
                    exerciseId, row.month(), row.actualVolume(), row.commercialRatio(),
                    row.manualForecastVolume(), ownerId, now));
        }
        return getMonthlyVolumes(ownerId, exerciseId);
    }

    /**
     * Lists daily volumes.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return daily volumes
     */
    @Transactional(readOnly = true)
    public List<DailyVolumeView> getDailyVolumes(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
        return dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(exerciseId).stream()
                .map(v -> new DailyVolumeView(
                        v.getId(), v.getVolumeDate(), v.getActualVolume(), v.getDailyAdjustmentRatio(),
                        v.getManualForecastVolume(), v.getSourceType()))
                .toList();
    }

    /**
     * Replaces the full daily volume list.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request daily rows
     * @return replaced list
     */
    @Transactional
    public List<DailyVolumeView> putDailyVolumes(
            UUID ownerId, UUID exerciseId, List<DailyVolumeRequest> request) {
        editable(ownerId, exerciseId);
        Instant now = clock.instant();
        dailyVolumes.deleteByExerciseId(exerciseId);
        dailyVolumes.flush();
        for (DailyVolumeRequest row : request) {
            dailyVolumes.save(ExerciseVolumeDailyInput.create(
                    exerciseId, row.volumeDate(), row.actualVolume(), row.dailyAdjustmentRatio(),
                    row.manualForecastVolume(), ownerId, now));
        }
        return getDailyVolumes(ownerId, exerciseId);
    }

    /**
     * Lists slot volumes.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return slot volumes
     */
    @Transactional(readOnly = true)
    public List<SlotVolumeView> getSlotVolumes(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
        return slotVolumes.findByExerciseIdOrderBySlotStartAtAsc(exerciseId).stream()
                .map(v -> new SlotVolumeView(
                        v.getId(), v.getSlotStartAt(), v.getSlotEndAt(), v.getRawVolume(),
                        v.getTimezone(), v.getSourceType()))
                .toList();
    }

    /**
     * Replaces the full slot volume list.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request slot rows
     * @return replaced list
     */
    @Transactional
    public List<SlotVolumeView> putSlotVolumes(
            UUID ownerId, UUID exerciseId, List<SlotVolumeRequest> request) {
        editable(ownerId, exerciseId);
        Instant now = clock.instant();
        for (SlotVolumeRequest row : request) {
            if (!row.slotEndAt().isAfter(row.slotStartAt())) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "invalid-slot-bounds",
                        "slotEndAt must be after slotStartAt.");
            }
        }
        slotVolumes.deleteByExerciseId(exerciseId);
        slotVolumes.flush();
        for (SlotVolumeRequest row : request) {
            slotVolumes.save(ExerciseVolumeSlotInput.create(
                    exerciseId, row.slotStartAt(), row.slotEndAt(), row.rawVolume(),
                    row.timezone(), ownerId, now));
        }
        return getSlotVolumes(ownerId, exerciseId);
    }

    private RstExercise editable(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerId, exerciseId);
        exercises.requireEditable(exercise);
        return exercise;
    }

    private ExerciseTeamSetup requireTeamSetup(UUID exerciseId) {
        return teamSetups.findById(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "team-setup-not-found", "Team Setup was not found."));
    }

    private BigDecimal activeCycleTimeSeconds(UUID exerciseId) {
        return cycleTimeBaselines.findByExerciseIdAndActiveTrue(exerciseId)
                .map(CycleTimeBaseline::getMedianSeconds)
                .orElse(null);
    }

    /**
     * Recomputes Hours/year and FTE for all active support rows from current Team Setup.
     */
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
                // Keep existing derived values for unrecognized historical frequency codes.
            }
        }
    }

    private ExerciseCalendar requireCalendar(UUID exerciseId) {
        return calendars.findById(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "calendar-not-found", "Calendar was not found."));
    }

    private List<UUID> resolveKpiIds(RstExercise exercise, List<UUID> requested) {
        List<UUID> all = exercise.getSharedKpiLines().stream().map(line -> line.getId()).toList();
        if (requested == null || requested.isEmpty()) {
            return all;
        }
        for (UUID id : requested) {
            if (!all.contains(id)) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "invalid-kpi-scope",
                        "Support scope must reference KPI lines of the same Exercise.");
            }
        }
        return requested;
    }

    private void replaceScopes(UUID itemId, List<UUID> kpiLineIds) {
        supportScopes.deleteByExerciseProductionSupportItemId(itemId);
        supportScopes.flush();
        if (kpiLineIds.isEmpty()) {
            return;
        }
        BigDecimal ratio = BigDecimal.ONE.divide(
                BigDecimal.valueOf(kpiLineIds.size()), 8, RoundingMode.HALF_UP);
        for (UUID kpiLineId : kpiLineIds) {
            supportScopes.save(ExerciseProductionSupportItemScope.assign(itemId, kpiLineId, ratio));
        }
    }

    private TeamSetupView toTeamSetup(ExerciseTeamSetup setup) {
        return new TeamSetupView(
                setup.getAgentsLt6m(), setup.getAgents6To24m(), setup.getAgents24To48m(),
                setup.getAgentsGt48m(), setup.getDeliveryHc(), setup.getWorkingHoursPerDay(),
                setup.getPaidLeaveDays(), setup.getOtherLeaveDays(), setup.getWeekendCode(),
                setup.getAvailabilityRatio(), setup.getAutomationRatio(), setup.getCapacityRatio(),
                setup.getMaxOvertimeMinutes(), setup.getSlaType(), setup.getSlaTargetRatio(),
                setup.getSlaTurnaroundMinutes(), setup.getSlaStartTime(), setup.getSlaEndTime(),
                setup.getSlaWeekendEnabled(), setup.getWeekendShiftHc(), setup.getSkeletonRatio(),
                setup.getTotalAgents(), setup.getAverageTenureYears(), setup.getWorkingDaysPerYear(),
                setup.getMaxCapacityDays(), setup.getDailyCapacityPerAgent(),
                setup.getCalculationVersion(), setup.getVersion());
    }

    private ShiftView toShift(ExerciseShift shift) {
        return new ShiftView(
                shift.getId(), shift.getShiftNo(), shift.getStartTime(), shift.getDurationMinutes(),
                shift.getHeadcount(), shift.isWorksOnWeekend());
    }

    private SupportItemView toSupport(ExerciseProductionSupportItem item) {
        List<SupportScopeView> scopes = supportScopes.findByExerciseProductionSupportItemId(item.getId()).stream()
                .map(s -> new SupportScopeView(s.getExerciseSharedKpiLineId(), s.getAllocationRatio()))
                .toList();
        return new SupportItemView(
                item.getId(), item.getLineageId(), item.getCategory(), item.getActivity(),
                item.getFrequencyCode(), item.getVolume(), item.getUnitOfMeasure(),
                item.getWorkloadPerUnitMinutes(), item.getAnnualMultiplier(),
                item.getWorkloadPerYearHours(), item.getSupportFte(), item.getComments(),
                item.getCalculationVersion(), scopes);
    }

    private HolidayView toHoliday(ExerciseHoliday holiday) {
        return new HolidayView(
                holiday.getId(), holiday.getHolidayDate(), holiday.getHolidayName(),
                holiday.getHolidayType(), holiday.getWorkingDayOverride());
    }

    /** Team Setup response. */
    public record TeamSetupView(
            BigDecimal agentsLt6m, BigDecimal agents6To24m, BigDecimal agents24To48m,
            BigDecimal agentsGt48m, BigDecimal deliveryHc, BigDecimal workingHoursPerDay,
            BigDecimal paidLeaveDays, BigDecimal otherLeaveDays, String weekendCode,
            BigDecimal availabilityRatio, BigDecimal automationRatio, BigDecimal capacityRatio,
            Integer maxOvertimeMinutes, String slaType, BigDecimal slaTargetRatio,
            Integer slaTurnaroundMinutes, LocalTime slaStartTime, LocalTime slaEndTime,
            Boolean slaWeekendEnabled, BigDecimal weekendShiftHc, BigDecimal skeletonRatio,
            BigDecimal totalAgents, BigDecimal averageTenureYears, BigDecimal workingDaysPerYear,
            BigDecimal maxCapacityDays, BigDecimal dailyCapacityPerAgent,
            String calculationVersion, long version) {
    }

    /** Team Setup PUT payload. */
    public record TeamSetupRequest(
            BigDecimal agentsLt6m, BigDecimal agents6To24m, BigDecimal agents24To48m,
            BigDecimal agentsGt48m, BigDecimal deliveryHc, BigDecimal workingHoursPerDay,
            BigDecimal paidLeaveDays, BigDecimal otherLeaveDays, String weekendCode,
            BigDecimal availabilityRatio, BigDecimal automationRatio, BigDecimal capacityRatio,
            Integer maxOvertimeMinutes, String slaType, BigDecimal slaTargetRatio,
            Integer slaTurnaroundMinutes, LocalTime slaStartTime, LocalTime slaEndTime,
            Boolean slaWeekendEnabled, BigDecimal weekendShiftHc, BigDecimal skeletonRatio) {
        /**
         * Converts to domain input.
         *
         * @return domain input
         */
        public TeamSetupInput toInput() {
            return new TeamSetupInput(
                    agentsLt6m, agents6To24m, agents24To48m, agentsGt48m, deliveryHc,
                    workingHoursPerDay, paidLeaveDays, otherLeaveDays, weekendCode,
                    availabilityRatio, automationRatio, capacityRatio, maxOvertimeMinutes,
                    slaType, slaTargetRatio, slaTurnaroundMinutes, slaStartTime, slaEndTime,
                    slaWeekendEnabled, weekendShiftHc, skeletonRatio);
        }
    }

    /** Shift response. */
    public record ShiftView(
            UUID id, short shiftNo, LocalTime startTime, int durationMinutes,
            BigDecimal headcount, boolean worksOnWeekend) {
    }

    /** Shift request row. */
    public record ShiftRequest(
            short shiftNo, @NotNull LocalTime startTime, int durationMinutes,
            @NotNull BigDecimal headcount, boolean worksOnWeekend) {
    }

    /** Support item response. */
    public record SupportItemView(
            UUID id, UUID lineageId, String category, String activity, String frequencyCode,
            BigDecimal volume, String unitOfMeasure, BigDecimal workloadPerUnitMinutes,
            BigDecimal annualMultiplier, BigDecimal workloadPerYearHours, BigDecimal supportFte,
            String comments, String calculationVersion, List<SupportScopeView> scopes) {
    }

    /** Support scope response. */
    public record SupportScopeView(UUID exerciseSharedKpiLineId, BigDecimal allocationRatio) {
    }

    /** Support item write payload. {@code annualMultiplier} is ignored; server derives it. */
    public record SupportItemRequest(
            @NotBlank String category,
            @NotBlank String activity,
            @NotBlank String frequencyCode,
            @NotNull BigDecimal volume,
            @NotBlank String unitOfMeasure,
            @NotNull BigDecimal workloadPerUnitMinutes,
            BigDecimal annualMultiplier,
            String comments,
            List<UUID> kpiLineIds) {
    }

    /** Calendar response. */
    public record CalendarView(
            String weekendCode, String baselineSource,
            String baselineVersion, UUID sourceTemplateId, Integer sourceTemplateVersion,
            Short baselineYear, BigDecimal workingDaysPerYear, long version,
            List<HolidayView> holidays,
            boolean templateUpdateAvailable,
            Integer publishedTemplateVersion,
            String templateUpdateMessage) {
    }

    /** Re-apply template response with initialization notices. */
    public record ReapplyCalendarResult(CalendarView calendar, List<String> notices) {
    }

    /** Calendar PUT payload. */
    public record CalendarRequest(
            String weekendCode, String baselineSource,
            String baselineVersion, List<HolidayRequest> holidays) {
    }

    /** Holiday response. */
    public record HolidayView(
            UUID id, LocalDate holidayDate, String holidayName, String holidayType,
            Boolean workingDayOverride) {
    }

    /** Holiday request row. */
    public record HolidayRequest(
            @NotNull LocalDate holidayDate, @NotBlank String holidayName,
            @NotBlank String holidayType, Boolean workingDayOverride) {
    }

    /** Monthly volume response. */
    public record MonthlyVolumeView(
            UUID id, String month, BigDecimal actualVolume, BigDecimal commercialRatio,
            BigDecimal manualForecastVolume, String sourceType) {
    }

    /** Monthly volume request row. */
    public record MonthlyVolumeRequest(
            @NotBlank String month, BigDecimal actualVolume, BigDecimal commercialRatio,
            BigDecimal manualForecastVolume) {
    }

    /** Daily volume response. */
    public record DailyVolumeView(
            UUID id, LocalDate volumeDate, BigDecimal actualVolume, BigDecimal dailyAdjustmentRatio,
            BigDecimal manualForecastVolume, String sourceType) {
    }

    /** Daily volume request row. */
    public record DailyVolumeRequest(
            @NotNull LocalDate volumeDate, BigDecimal actualVolume, BigDecimal dailyAdjustmentRatio,
            BigDecimal manualForecastVolume) {
    }

    /** Slot volume response. */
    public record SlotVolumeView(
            UUID id, Instant slotStartAt, Instant slotEndAt, BigDecimal rawVolume,
            String timezone, String sourceType) {
    }

    /** Slot volume request row. */
    public record SlotVolumeRequest(
            @NotNull Instant slotStartAt, @NotNull Instant slotEndAt,
            @NotNull BigDecimal rawVolume, @NotBlank String timezone) {
    }
}
