package com.cmacgm.gbs.rst.api.associateddata.application;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.cmacgm.gbs.rst.api.associateddata.domain.DataImportBatch;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseCalendar;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseShift;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup.TeamSetupInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeDailyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeMonthlyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeSlotInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.FileArtifact;
import com.cmacgm.gbs.rst.api.associateddata.persistence.DataImportBatchRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseCalendarRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseShiftRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseVolumeSlotInputRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.FileArtifactRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseInitializationService;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.ApplyTemplatesResult;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.TemplateUpdateHint;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.CalendarRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.CalendarView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.DailyVolumeRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.DailyVolumeView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.HolidayRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.HolidayView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.MonthlyVolumeRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.MonthlyVolumeView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ReapplyCalendarResult;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ShiftRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ShiftView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SlotVolumeRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SlotVolumeView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SupportItemRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SupportItemView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.TeamSetupRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.TeamSetupView;

/**
 * Associated Data CRUD for Supervisor Exercise Team Setup, Shift, Support, Calendar and Volume.
 */
@Service
public class AssociatedDataService {

    private final ExerciseAccess exercises;
    private final ExerciseTeamSetupRepository teamSetups;
    private final ExerciseShiftRepository shifts;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseCalendarRepository calendars;
    private final ExerciseHolidayRepository holidays;
    private final ExerciseVolumeMonthlyInputRepository monthlyVolumes;
    private final ExerciseVolumeDailyInputRepository dailyVolumes;
    private final ExerciseVolumeSlotInputRepository slotVolumes;
    private final HolidayTemplateService holidayTemplates;
    private final CycleTimeBaselineRepository cycleTimeBaselines;
    private final VolumeInputValidator volumeValidator;
    private final VolumeExcelService volumeExcel;
    private final FileArtifactRepository fileArtifacts;
    private final DataImportBatchRepository importBatches;
    private final Clock clock;

    /**
     * Creates the Associated Data service.
     */
    public AssociatedDataService(
            ExerciseAccess exercises,
            ExerciseTeamSetupRepository teamSetups,
            ExerciseShiftRepository shifts,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseCalendarRepository calendars,
            ExerciseHolidayRepository holidays,
            ExerciseVolumeMonthlyInputRepository monthlyVolumes,
            ExerciseVolumeDailyInputRepository dailyVolumes,
            ExerciseVolumeSlotInputRepository slotVolumes,
            HolidayTemplateService holidayTemplates,
            CycleTimeBaselineRepository cycleTimeBaselines,
            VolumeInputValidator volumeValidator,
            VolumeExcelService volumeExcel,
            FileArtifactRepository fileArtifacts,
            DataImportBatchRepository importBatches,
            Clock clock) {
        this.exercises = exercises;
        this.teamSetups = teamSetups;
        this.shifts = shifts;
        this.supportItems = supportItems;
        this.calendars = calendars;
        this.holidays = holidays;
        this.monthlyVolumes = monthlyVolumes;
        this.dailyVolumes = dailyVolumes;
        this.slotVolumes = slotVolumes;
        this.holidayTemplates = holidayTemplates;
        this.cycleTimeBaselines = cycleTimeBaselines;
        this.volumeValidator = volumeValidator;
        this.volumeExcel = volumeExcel;
        this.fileArtifacts = fileArtifacts;
        this.importBatches = importBatches;
        this.clock = clock;
    }

    /**
     * Returns Team Setup for an Exercise.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return Team Setup view
     */
    @Transactional(readOnly = true)
    public TeamSetupView getTeamSetup(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.requireReadable(ownerCcgid, exerciseId);
        return toTeamSetup(exercise, requireTeamSetup(exerciseId));
    }

    /**
     * Replaces Team Setup inputs. Derived metrics are computed on the response.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param request Team Setup payload
     * @return updated Team Setup view
     */
    @Transactional
    public TeamSetupView putTeamSetup(String ownerCcgid, UUID exerciseId, TeamSetupRequest request) {
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        ExerciseTeamSetup setup = requireTeamSetup(exercise.getId());
        Instant now = clock.instant();
        setup.replaceInputs(request.toInput(), ownerCcgid, now);
        return toTeamSetup(exercise, teamSetups.save(setup));
    }

    /**
     * Lists active shifts.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return shifts
     */
    @Transactional(readOnly = true)
    public List<ShiftView> getShifts(String ownerCcgid, UUID exerciseId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        return shifts.findByExerciseIdAndDeletedAtIsNullOrderByShiftNoAsc(exerciseId).stream()
                .map(this::toShift)
                .toList();
    }

    /**
     * Replaces the full active shift list for an Exercise.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param request shift list
     * @return new active shifts
     */
    @Transactional
    public List<ShiftView> putShifts(String ownerCcgid, UUID exerciseId, List<ShiftRequest> request) {
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        Instant now = clock.instant();
        // Upsert by shift_no to avoid unique (exercise_id, shift_no) conflicts from
        // soft-delete + insert in the same flush (partial index WHERE deleted_at IS NULL).
        Map<Short, ExerciseShift> existingByNo = new LinkedHashMap<>();
        for (ExerciseShift existing : shifts.findByExerciseIdAndDeletedAtIsNullOrderByShiftNoAsc(exerciseId)) {
            existingByNo.put(existing.getShiftNo(), existing);
        }
        Set<Short> kept = new HashSet<>();
        for (ShiftRequest item : request) {
            kept.add(item.shiftNo());
            ExerciseShift current = existingByNo.get(item.shiftNo());
            if (current != null) {
                current.replace(
                        item.startTime(),
                        item.durationMinutes(),
                        item.headcount(),
                        item.worksOnWeekend(),
                        ownerCcgid,
                        now);
            } else {
                shifts.save(ExerciseShift.create(
                        exercise.getId(),
                        item.shiftNo(),
                        item.startTime(),
                        item.durationMinutes(),
                        item.headcount(),
                        item.worksOnWeekend(),
                        ownerCcgid,
                        now));
            }
        }
        for (Map.Entry<Short, ExerciseShift> entry : existingByNo.entrySet()) {
            if (!kept.contains(entry.getKey())) {
                entry.getValue().softDelete(ownerCcgid, now);
            }
        }
        return getShifts(ownerCcgid, exerciseId);
    }

    /**
     * Lists active production support items for an Exercise.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return support items
     */
    @Transactional(readOnly = true)
    public List<SupportItemView> listSupport(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.requireReadable(ownerCcgid, exerciseId);
        return supportItems.findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(exercise.getId())
                .stream()
                .map(item -> toSupport(item, exerciseId))
                .toList();
    }

    /**
     * Creates a support item for the Exercise.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param request support payload
     * @return created item
     */
    @Transactional
    public SupportItemView createSupport(String ownerCcgid, UUID exerciseId, SupportItemRequest request) {
        editable(ownerCcgid, exerciseId);
        Instant now = clock.instant();
        requireValidFrequency(request.frequencyCode(), exerciseId);
        ExerciseProductionSupportItem item = ExerciseProductionSupportItem.create(
                exerciseId, request.category(), request.activity(), request.frequencyCode(),
                request.volume(), request.unitOfMeasure(), request.workloadPerUnitMinutes(),
                request.comments(), ownerCcgid, now);
        supportItems.save(item);
        return toSupport(item, exerciseId);
    }

    /**
     * Updates a support item.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param itemId support item id
     * @param request support payload
     * @return updated item
     */
    @Transactional
    public SupportItemView updateSupport(
            String ownerCcgid, UUID exerciseId, UUID itemId, SupportItemRequest request) {
        editable(ownerCcgid, exerciseId);
        ExerciseProductionSupportItem item = supportItems.findByIdAndExerciseIdAndDeletedAtIsNull(itemId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "support-item-not-found", "The support item was not found."));
        Instant now = clock.instant();
        requireValidFrequency(request.frequencyCode(), exerciseId);
        item.update(
                request.category(), request.activity(), request.frequencyCode(), request.volume(),
                request.unitOfMeasure(), request.workloadPerUnitMinutes(),
                request.comments(), ownerCcgid, now);
        supportItems.save(item);
        return toSupport(item, exerciseId);
    }

    /**
     * Soft-deletes a support item.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param itemId support item id
     */
    @Transactional
    public void deleteSupport(String ownerCcgid, UUID exerciseId, UUID itemId) {
        editable(ownerCcgid, exerciseId);
        ExerciseProductionSupportItem item = supportItems.findByIdAndExerciseIdAndDeletedAtIsNull(itemId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "support-item-not-found", "The support item was not found."));
        item.softDelete(ownerCcgid, clock.instant());
        supportItems.save(item);
    }

    /**
     * Returns calendar header and active holidays.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return calendar view
     */
    @Transactional(readOnly = true)
    public CalendarView getCalendar(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.requireReadable(ownerCcgid, exerciseId);
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
                calendar.getBaselineYear(), holidayTemplates.workingDaysPerYear(calendar),
                calendar.getVersion(), holidayViews,
                update != null,
                update != null ? update.publishedVersion() : null,
                update != null ? update.message() : null);
    }

    /**
     * Replaces calendar header and full holiday list.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param request calendar payload
     * @return updated calendar
     */
    @Transactional
    public CalendarView putCalendar(String ownerCcgid, UUID exerciseId, CalendarRequest request) {
        editable(ownerCcgid, exerciseId);
        Instant now = clock.instant();
        ExerciseCalendar calendar = requireCalendar(exerciseId);
        calendar.replace(
                request.weekendCode(),
                request.baselineSource(), request.baselineVersion(), ownerCcgid, now);
        calendars.save(calendar);
        for (ExerciseHoliday existing : holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId)) {
            existing.softDelete(ownerCcgid, now);
            holidays.save(existing);
        }
        holidays.flush();
        if (request.holidays() != null) {
            for (HolidayRequest holiday : request.holidays()) {
                holidays.save(ExerciseHoliday.create(
                        exerciseId, holiday.holidayDate(), holiday.holidayName(),
                        holiday.holidayType(), ownerCcgid, now));
            }
        }
        return getCalendar(ownerCcgid, exerciseId);
    }

    /**
     * Re-applies the Center holiday template for the Exercise sizing year.
     * Preserves CUSTOM holidays.
     */
    @Transactional
    public ReapplyCalendarResult reapplyHolidayTemplate(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        String center = exercise.getToolkitSnapshot() != null
                ? exercise.getToolkitSnapshot().getCenter()
                : null;
        short primaryYear = (short) YearMonth.from(exercise.getSizingMonth()).getYear();
        ApplyTemplatesResult applied = holidayTemplates.applyPublishedTemplates(
                exerciseId,
                center,
                primaryYear,
                ExerciseInitializationService.resolveHolidayYears(exercise),
                ownerCcgid,
                true);
        return new ReapplyCalendarResult(getCalendar(ownerCcgid, exerciseId), applied.notices());
    }

    /**
     * Lists monthly volumes.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return monthly volumes
     */
    @Transactional(readOnly = true)
    public List<MonthlyVolumeView> getMonthlyVolumes(String ownerCcgid, UUID exerciseId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        return monthlyVolumes.findByExerciseIdOrderByMonthAsc(exerciseId).stream()
                .map(v -> new MonthlyVolumeView(
                        v.getId(),
                        MonthKeys.formatYearMonth(v.getMonth()),
                        v.getActualVolume(),
                        v.getSourceType(),
                        v.getImportBatchId()))
                .toList();
    }

    /**
     * Replaces the full monthly volume list.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param request monthly rows
     * @return replaced list
     */
    @Transactional
    public List<MonthlyVolumeView> putMonthlyVolumes(
            String ownerCcgid, UUID exerciseId, List<MonthlyVolumeRequest> request) {
        return replaceMonthlyVolumes(ownerCcgid, exerciseId, request, "MANUAL", null);
    }

    /**
     * Exports a blank monthly volume Excel template.
     */
    @Transactional(readOnly = true)
    public byte[] exportMonthlyTemplate(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        return volumeExcel.exportMonthlyBlank();
    }

    /**
     * Exports current monthly volumes as Excel.
     */
    @Transactional(readOnly = true)
    public byte[] exportMonthlyExcel(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        List<MonthlyVolumeRequest> rows = monthlyVolumes.findByExerciseIdOrderByMonthAsc(exerciseId).stream()
                .map(v -> new MonthlyVolumeRequest(MonthKeys.formatYearMonth(v.getMonth()), v.getActualVolume()))
                .toList();
        return volumeExcel.exportMonthly(rows);
    }

    /**
     * Imports monthly volumes from Excel (replace).
     */
    @Transactional
    public List<MonthlyVolumeView> importMonthlyExcel(
            String ownerCcgid, UUID exerciseId, InputStream input, String fileName) {
        editable(ownerCcgid, exerciseId);
        List<MonthlyVolumeRequest> parsed = volumeExcel.parseMonthly(input);
        volumeValidator.validateMonthly(parsed);
        UUID batchId = recordImportBatch(ownerCcgid, exerciseId, "MONTHLY_VOLUME", fileName, parsed.size());
        return replaceMonthlyVolumes(ownerCcgid, exerciseId, parsed, "IMPORT", batchId);
    }

    /**
     * Lists daily volumes.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return daily volumes
     */
    @Transactional(readOnly = true)
    public List<DailyVolumeView> getDailyVolumes(String ownerCcgid, UUID exerciseId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        return dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(exerciseId).stream()
                .map(v -> new DailyVolumeView(
                        v.getId(),
                        v.getVolumeDate(),
                        v.getActualVolume(),
                        v.getSourceType(),
                        v.getImportBatchId()))
                .toList();
    }

    /**
     * Replaces the full daily volume list.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param request daily rows
     * @return replaced list
     */
    @Transactional
    public List<DailyVolumeView> putDailyVolumes(
            String ownerCcgid, UUID exerciseId, List<DailyVolumeRequest> request) {
        return replaceDailyVolumes(ownerCcgid, exerciseId, request, "MANUAL", null);
    }

    /**
     * Exports a blank daily volume Excel template.
     */
    @Transactional(readOnly = true)
    public byte[] exportDailyTemplate(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        return volumeExcel.exportDailyBlank();
    }

    /**
     * Exports current daily volumes as Excel.
     */
    @Transactional(readOnly = true)
    public byte[] exportDailyExcel(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        List<DailyVolumeRequest> rows = dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(exerciseId).stream()
                .map(v -> new DailyVolumeRequest(v.getVolumeDate(), v.getActualVolume()))
                .toList();
        return volumeExcel.exportDaily(rows);
    }

    /**
     * Imports daily volumes from Excel (replace).
     */
    @Transactional
    public List<DailyVolumeView> importDailyExcel(
            String ownerCcgid, UUID exerciseId, InputStream input, String fileName) {
        editable(ownerCcgid, exerciseId);
        List<DailyVolumeRequest> parsed = volumeExcel.parseDaily(input);
        volumeValidator.validateDaily(parsed);
        UUID batchId = recordImportBatch(ownerCcgid, exerciseId, "DAILY_VOLUME", fileName, parsed.size());
        return replaceDailyVolumes(ownerCcgid, exerciseId, parsed, "IMPORT", batchId);
    }

    /**
     * Lists slot volumes.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return slot volumes
     */
    @Transactional(readOnly = true)
    public List<SlotVolumeView> getSlotVolumes(String ownerCcgid, UUID exerciseId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        return slotVolumes.findByExerciseIdOrderBySlotStartAtAsc(exerciseId).stream()
                .map(v -> new SlotVolumeView(
                        v.getId(),
                        v.getSlotStartAt(),
                        v.getSlotEndAt(),
                        v.getActualVolume(),
                        v.getSourceType(),
                        v.getImportBatchId()))
                .toList();
    }

    /**
     * Replaces the full slot volume list.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @param request slot rows
     * @return replaced list
     */
    @Transactional
    public List<SlotVolumeView> putSlotVolumes(
            String ownerCcgid, UUID exerciseId, List<SlotVolumeRequest> request) {
        return replaceSlotVolumes(ownerCcgid, exerciseId, request, "MANUAL", null);
    }

    /**
     * Exports a blank slot volume Excel template.
     */
    @Transactional(readOnly = true)
    public byte[] exportSlotTemplate(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        return volumeExcel.exportSlotBlank();
    }

    /**
     * Exports current slot volumes as Excel.
     */
    @Transactional(readOnly = true)
    public byte[] exportSlotExcel(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        List<SlotVolumeRequest> rows = slotVolumes.findByExerciseIdOrderBySlotStartAtAsc(exerciseId).stream()
                .map(v -> new SlotVolumeRequest(
                        v.getSlotStartAt(), v.getSlotEndAt(), v.getActualVolume()))
                .toList();
        return volumeExcel.exportSlot(rows);
    }

    /**
     * Imports slot volumes from Excel (replace).
     */
    @Transactional
    public List<SlotVolumeView> importSlotExcel(
            String ownerCcgid, UUID exerciseId, InputStream input, String fileName) {
        editable(ownerCcgid, exerciseId);
        List<SlotVolumeRequest> parsed = volumeExcel.parseSlot(input);
        volumeValidator.validateSlot(parsed);
        UUID batchId = recordImportBatch(ownerCcgid, exerciseId, "SLOT_VOLUME", fileName, parsed.size());
        return replaceSlotVolumes(ownerCcgid, exerciseId, parsed, "IMPORT", batchId);
    }

    private List<MonthlyVolumeView> replaceMonthlyVolumes(
            String ownerCcgid,
            UUID exerciseId,
            List<MonthlyVolumeRequest> request,
            String sourceType,
            UUID importBatchId) {
        editable(ownerCcgid, exerciseId);
        volumeValidator.validateMonthly(request);
        Instant now = clock.instant();
        monthlyVolumes.deleteByExerciseId(exerciseId);
        monthlyVolumes.flush();
        List<ExerciseVolumeMonthlyInput> rows = new ArrayList<>(request.size());
        for (MonthlyVolumeRequest row : request) {
            rows.add(ExerciseVolumeMonthlyInput.create(
                    exerciseId,
                    MonthKeys.parseMonthStart(row.month()),
                    row.actualVolume(),
                    sourceType,
                    importBatchId,
                    ownerCcgid,
                    now));
        }
        monthlyVolumes.saveAll(rows);
        return getMonthlyVolumes(ownerCcgid, exerciseId);
    }

    private List<DailyVolumeView> replaceDailyVolumes(
            String ownerCcgid,
            UUID exerciseId,
            List<DailyVolumeRequest> request,
            String sourceType,
            UUID importBatchId) {
        editable(ownerCcgid, exerciseId);
        volumeValidator.validateDaily(request);
        Instant now = clock.instant();
        dailyVolumes.deleteByExerciseId(exerciseId);
        dailyVolumes.flush();
        List<ExerciseVolumeDailyInput> rows = new ArrayList<>(request.size());
        for (DailyVolumeRequest row : request) {
            rows.add(ExerciseVolumeDailyInput.create(
                    exerciseId,
                    row.volumeDate(),
                    row.actualVolume(),
                    sourceType,
                    importBatchId,
                    ownerCcgid,
                    now));
        }
        dailyVolumes.saveAll(rows);
        return getDailyVolumes(ownerCcgid, exerciseId);
    }

    private List<SlotVolumeView> replaceSlotVolumes(
            String ownerCcgid,
            UUID exerciseId,
            List<SlotVolumeRequest> request,
            String sourceType,
            UUID importBatchId) {
        editable(ownerCcgid, exerciseId);
        volumeValidator.validateSlot(request);
        Instant now = clock.instant();
        slotVolumes.deleteByExerciseId(exerciseId);
        slotVolumes.flush();
        List<ExerciseVolumeSlotInput> rows = new ArrayList<>(request.size());
        for (SlotVolumeRequest row : request) {
            rows.add(ExerciseVolumeSlotInput.create(
                    exerciseId,
                    row.slotStartAt(),
                    row.slotEndAt(),
                    row.actualVolume(),
                    sourceType,
                    importBatchId,
                    ownerCcgid,
                    now));
        }
        slotVolumes.saveAll(rows);
        return getSlotVolumes(ownerCcgid, exerciseId);
    }

    private UUID recordImportBatch(
            String ownerCcgid, UUID exerciseId, String importType, String fileName, int rowCount) {
        Instant now = clock.instant();
        FileArtifact artifact = fileArtifacts.save(FileArtifact.createStub(
                "VOLUME_IMPORT",
                "EXERCISE",
                exerciseId,
                fileName == null || fileName.isBlank() ? "volume-import.xlsx" : fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ownerCcgid,
                now));
        DataImportBatch batch = importBatches.save(DataImportBatch.create(
                exerciseId,
                importType,
                artifact.getId(),
                "IMPORTED",
                rowCount,
                rowCount,
                0,
                "{\"accepted\":" + rowCount + "}",
                ownerCcgid,
                now));
        return batch.getId();
    }

    private RstExercise editable(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
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

    private ExerciseCalendar requireCalendar(UUID exerciseId) {
        return calendars.findById(exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "calendar-not-found", "Calendar was not found."));
    }

    private TeamSetupView toTeamSetup(RstExercise exercise, ExerciseTeamSetup setup) {
        ExerciseCalendar calendar = calendars.findById(exercise.getId()).orElse(null);
        BigDecimal workingDays = holidayTemplates.workingDaysPerYear(exercise.getId());
        BigDecimal cycleTime = activeCycleTimeSeconds(exercise.getId());
        String weekend = calendar != null ? calendar.getWeekendCode() : null;
        return new TeamSetupView(
                setup.getAgentsLt6m(), setup.getAgents6To24m(), setup.getAgents24To48m(),
                setup.getAgentsGt48m(), deliveryHc(exercise), setup.workingHoursPerDay(),
                setup.getPaidLeaveDays(), setup.getOtherLeaveDays(), weekend,
                setup.getAvailabilityRatio(), setup.getAutomationRatio(), setup.capacityRatio(workingDays),
                setup.getMaxOvertimeMinutes(), setup.getSlaType(), setup.getSlaTargetRatio(),
                setup.getSlaTurnaroundMinutes(), setup.getSlaStartTime(), setup.getSlaEndTime(),
                setup.getSlaWeekendEnabled(), setup.getWeekendShiftHc(), setup.getSkeletonRatio(),
                setup.totalAgents(), setup.averageTenureYears(), workingDays,
                setup.maxCapacityDays(workingDays), setup.dailyCapacityPerAgent(cycleTime),
                null, setup.getVersion());
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

    private void requireValidFrequency(String frequencyCode, UUID exerciseId) {
        try {
            SupportWorkloadMath.annualMultiplier(
                    frequencyCode, holidayTemplates.workingDaysPerYear(exerciseId));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "invalid-frequency", ex.getMessage());
        }
    }

    private ShiftView toShift(ExerciseShift shift) {
        return new ShiftView(
                shift.getId(), shift.getShiftNo(), shift.getStartTime(), shift.getDurationMinutes(),
                shift.getHeadcount(), shift.isWorksOnWeekend());
    }

    private SupportItemView toSupport(ExerciseProductionSupportItem item, UUID exerciseId) {
        ExerciseTeamSetup setup = teamSetups.findById(exerciseId).orElse(null);
        BigDecimal workingDays = holidayTemplates.workingDaysPerYear(exerciseId);
        BigDecimal fteHours = SupportWorkloadMath.fteAnnualHours(setup, workingDays);
        SupportWorkloadMath.Derived derived = SupportWorkloadMath.derive(item, workingDays, fteHours);
        return new SupportItemView(
                item.getId(), item.getLineageId(), item.getCategory(), item.getActivity(),
                item.getFrequencyCode(), item.getVolume(), item.getUnitOfMeasure(),
                item.getWorkloadPerUnitMinutes(), derived.annualMultiplier(),
                derived.workloadPerYearHours(), derived.supportFte(), item.getComments(),
                null);
    }

    private HolidayView toHoliday(ExerciseHoliday holiday) {
        return new HolidayView(
                holiday.getId(), holiday.getHolidayDate(), holiday.getHolidayName(),
                holiday.getHolidayType());
    }
}
