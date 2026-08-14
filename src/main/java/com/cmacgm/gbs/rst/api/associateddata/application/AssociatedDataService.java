package com.cmacgm.gbs.rst.api.associateddata.application;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalTime;
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
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
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
            ExerciseService exercises,
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
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return Team Setup view
     */
    @Transactional(readOnly = true)
    public TeamSetupView getTeamSetup(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = exercises.requireReadable(ownerId, exerciseId);
        return toTeamSetup(exercise, requireTeamSetup(exerciseId));
    }

    /**
     * Replaces Team Setup inputs. Derived metrics are computed on the response.
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
        Instant now = clock.instant();
        setup.replaceInputs(request.toInput(), ownerId, now);
        return toTeamSetup(exercise, teamSetups.save(setup));
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
        exercises.requireReadable(ownerId, exerciseId);
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
                        ownerId,
                        now);
            } else {
                shifts.save(ExerciseShift.create(
                        exercise.getId(),
                        item.shiftNo(),
                        item.startTime(),
                        item.durationMinutes(),
                        item.headcount(),
                        item.worksOnWeekend(),
                        ownerId,
                        now));
            }
        }
        for (Map.Entry<Short, ExerciseShift> entry : existingByNo.entrySet()) {
            if (!kept.contains(entry.getKey())) {
                entry.getValue().softDelete(ownerId, now);
            }
        }
        return getShifts(ownerId, exerciseId);
    }

    /**
     * Lists active production support items for an Exercise.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @return support items
     */
    @Transactional(readOnly = true)
    public List<SupportItemView> listSupport(UUID ownerId, UUID exerciseId) {
        RstExercise exercise = exercises.requireReadable(ownerId, exerciseId);
        return supportItems.findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(exercise.getId())
                .stream()
                .map(item -> toSupport(item, exerciseId))
                .toList();
    }

    /**
     * Creates a support item for the Exercise.
     *
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request support payload
     * @return created item
     */
    @Transactional
    public SupportItemView createSupport(UUID ownerId, UUID exerciseId, SupportItemRequest request) {
        editable(ownerId, exerciseId);
        Instant now = clock.instant();
        requireValidFrequency(request.frequencyCode(), exerciseId);
        ExerciseProductionSupportItem item = ExerciseProductionSupportItem.create(
                exerciseId, request.category(), request.activity(), request.frequencyCode(),
                request.volume(), request.unitOfMeasure(), request.workloadPerUnitMinutes(),
                request.comments(), ownerId, now);
        supportItems.save(item);
        return toSupport(item, exerciseId);
    }

    /**
     * Updates a support item.
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
        editable(ownerId, exerciseId);
        ExerciseProductionSupportItem item = supportItems.findByIdAndExerciseIdAndDeletedAtIsNull(itemId, exerciseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "support-item-not-found", "The support item was not found."));
        Instant now = clock.instant();
        requireValidFrequency(request.frequencyCode(), exerciseId);
        item.update(
                request.category(), request.activity(), request.frequencyCode(), request.volume(),
                request.unitOfMeasure(), request.workloadPerUnitMinutes(),
                request.comments(), ownerId, now);
        supportItems.save(item);
        return toSupport(item, exerciseId);
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
        RstExercise exercise = exercises.requireReadable(ownerId, exerciseId);
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
        short primaryYear = (short) YearMonth.from(exercise.getSizingMonth()).getYear();
        ApplyTemplatesResult applied = holidayTemplates.applyPublishedTemplates(
                exerciseId,
                center,
                primaryYear,
                ExerciseInitializationService.resolveHolidayYears(exercise),
                ownerId,
                true);
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
        exercises.requireReadable(ownerId, exerciseId);
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
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request monthly rows
     * @return replaced list
     */
    @Transactional
    public List<MonthlyVolumeView> putMonthlyVolumes(
            UUID ownerId, UUID exerciseId, List<MonthlyVolumeRequest> request) {
        return replaceMonthlyVolumes(ownerId, exerciseId, request, "MANUAL", null);
    }

    /**
     * Exports a blank monthly volume Excel template.
     */
    @Transactional(readOnly = true)
    public byte[] exportMonthlyTemplate(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
        return volumeExcel.exportMonthlyBlank();
    }

    /**
     * Exports current monthly volumes as Excel.
     */
    @Transactional(readOnly = true)
    public byte[] exportMonthlyExcel(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
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
            UUID ownerId, UUID exerciseId, InputStream input, String fileName) {
        editable(ownerId, exerciseId);
        List<MonthlyVolumeRequest> parsed = volumeExcel.parseMonthly(input);
        volumeValidator.validateMonthly(parsed);
        UUID batchId = recordImportBatch(ownerId, exerciseId, "MONTHLY_VOLUME", fileName, parsed.size());
        return replaceMonthlyVolumes(ownerId, exerciseId, parsed, "IMPORT", batchId);
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
        exercises.requireReadable(ownerId, exerciseId);
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
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request daily rows
     * @return replaced list
     */
    @Transactional
    public List<DailyVolumeView> putDailyVolumes(
            UUID ownerId, UUID exerciseId, List<DailyVolumeRequest> request) {
        return replaceDailyVolumes(ownerId, exerciseId, request, "MANUAL", null);
    }

    /**
     * Exports a blank daily volume Excel template.
     */
    @Transactional(readOnly = true)
    public byte[] exportDailyTemplate(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
        return volumeExcel.exportDailyBlank();
    }

    /**
     * Exports current daily volumes as Excel.
     */
    @Transactional(readOnly = true)
    public byte[] exportDailyExcel(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
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
            UUID ownerId, UUID exerciseId, InputStream input, String fileName) {
        editable(ownerId, exerciseId);
        List<DailyVolumeRequest> parsed = volumeExcel.parseDaily(input);
        volumeValidator.validateDaily(parsed);
        UUID batchId = recordImportBatch(ownerId, exerciseId, "DAILY_VOLUME", fileName, parsed.size());
        return replaceDailyVolumes(ownerId, exerciseId, parsed, "IMPORT", batchId);
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
        exercises.requireReadable(ownerId, exerciseId);
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
     * @param ownerId Supervisor id
     * @param exerciseId Exercise id
     * @param request slot rows
     * @return replaced list
     */
    @Transactional
    public List<SlotVolumeView> putSlotVolumes(
            UUID ownerId, UUID exerciseId, List<SlotVolumeRequest> request) {
        return replaceSlotVolumes(ownerId, exerciseId, request, "MANUAL", null);
    }

    /**
     * Exports a blank slot volume Excel template.
     */
    @Transactional(readOnly = true)
    public byte[] exportSlotTemplate(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
        return volumeExcel.exportSlotBlank();
    }

    /**
     * Exports current slot volumes as Excel.
     */
    @Transactional(readOnly = true)
    public byte[] exportSlotExcel(UUID ownerId, UUID exerciseId) {
        exercises.requireOwned(ownerId, exerciseId);
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
            UUID ownerId, UUID exerciseId, InputStream input, String fileName) {
        editable(ownerId, exerciseId);
        List<SlotVolumeRequest> parsed = volumeExcel.parseSlot(input);
        volumeValidator.validateSlot(parsed);
        UUID batchId = recordImportBatch(ownerId, exerciseId, "SLOT_VOLUME", fileName, parsed.size());
        return replaceSlotVolumes(ownerId, exerciseId, parsed, "IMPORT", batchId);
    }

    private List<MonthlyVolumeView> replaceMonthlyVolumes(
            UUID ownerId,
            UUID exerciseId,
            List<MonthlyVolumeRequest> request,
            String sourceType,
            UUID importBatchId) {
        editable(ownerId, exerciseId);
        volumeValidator.validateMonthly(request);
        Instant now = clock.instant();
        monthlyVolumes.deleteByExerciseId(exerciseId);
        monthlyVolumes.flush();
        for (MonthlyVolumeRequest row : request) {
            monthlyVolumes.save(ExerciseVolumeMonthlyInput.create(
                    exerciseId,
                    MonthKeys.parseMonthStart(row.month()),
                    row.actualVolume(),
                    sourceType,
                    importBatchId,
                    ownerId,
                    now));
        }
        return getMonthlyVolumes(ownerId, exerciseId);
    }

    private List<DailyVolumeView> replaceDailyVolumes(
            UUID ownerId,
            UUID exerciseId,
            List<DailyVolumeRequest> request,
            String sourceType,
            UUID importBatchId) {
        editable(ownerId, exerciseId);
        volumeValidator.validateDaily(request);
        Instant now = clock.instant();
        dailyVolumes.deleteByExerciseId(exerciseId);
        dailyVolumes.flush();
        for (DailyVolumeRequest row : request) {
            dailyVolumes.save(ExerciseVolumeDailyInput.create(
                    exerciseId,
                    row.volumeDate(),
                    row.actualVolume(),
                    sourceType,
                    importBatchId,
                    ownerId,
                    now));
        }
        return getDailyVolumes(ownerId, exerciseId);
    }

    private List<SlotVolumeView> replaceSlotVolumes(
            UUID ownerId,
            UUID exerciseId,
            List<SlotVolumeRequest> request,
            String sourceType,
            UUID importBatchId) {
        editable(ownerId, exerciseId);
        volumeValidator.validateSlot(request);
        Instant now = clock.instant();
        slotVolumes.deleteByExerciseId(exerciseId);
        slotVolumes.flush();
        for (SlotVolumeRequest row : request) {
            slotVolumes.save(ExerciseVolumeSlotInput.create(
                    exerciseId,
                    row.slotStartAt(),
                    row.slotEndAt(),
                    row.actualVolume(),
                    sourceType,
                    importBatchId,
                    ownerId,
                    now));
        }
        return getSlotVolumes(ownerId, exerciseId);
    }

    private UUID recordImportBatch(
            UUID ownerId, UUID exerciseId, String importType, String fileName, int rowCount) {
        Instant now = clock.instant();
        FileArtifact artifact = fileArtifacts.save(FileArtifact.createStub(
                "VOLUME_IMPORT",
                "EXERCISE",
                exerciseId,
                fileName == null || fileName.isBlank() ? "volume-import.xlsx" : fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ownerId,
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
                ownerId,
                now));
        return batch.getId();
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
                holiday.getHolidayType(), holiday.getWorkingDayOverride());
    }

    /** Team Setup response. */
    public record TeamSetupView(
            BigDecimal agentsLt6m, BigDecimal agents6To24m, BigDecimal agents24To48m,
            BigDecimal agentsGt48m, BigDecimal deliveryHc, BigDecimal workingHoursPerDay,
            BigDecimal paidLeaveDays, BigDecimal otherLeaveDays, String weekendCode,
            BigDecimal availabilityRatio, BigDecimal automationRatio, BigDecimal capacityRatio,
            BigDecimal maxOvertimeMinutes, String slaType, BigDecimal slaTargetRatio,
            BigDecimal slaTurnaroundMinutes, LocalTime slaStartTime, LocalTime slaEndTime,
            Boolean slaWeekendEnabled, BigDecimal weekendShiftHc, BigDecimal skeletonRatio,
            BigDecimal totalAgents, BigDecimal averageTenureYears, BigDecimal workingDaysPerYear,
            BigDecimal maxCapacityDays, BigDecimal dailyCapacityPerAgent,
            String calculationVersion, long version) {
    }

    /** Team Setup PUT payload. */
    public record TeamSetupRequest(
            BigDecimal agentsLt6m, BigDecimal agents6To24m, BigDecimal agents24To48m,
            BigDecimal agentsGt48m, BigDecimal paidLeaveDays, BigDecimal otherLeaveDays,
            BigDecimal availabilityRatio, BigDecimal automationRatio,
            BigDecimal maxOvertimeMinutes, String slaType, BigDecimal slaTargetRatio,
            BigDecimal slaTurnaroundMinutes, LocalTime slaStartTime, LocalTime slaEndTime,
            Boolean slaWeekendEnabled, BigDecimal weekendShiftHc, BigDecimal skeletonRatio) {
        /**
         * Converts to domain input.
         *
         * @return domain input
         */
        public TeamSetupInput toInput() {
            return new TeamSetupInput(
                    agentsLt6m, agents6To24m, agents24To48m, agentsGt48m,
                    paidLeaveDays, otherLeaveDays,
                    availabilityRatio, automationRatio, maxOvertimeMinutes,
                    slaType, slaTargetRatio, slaTurnaroundMinutes, slaStartTime, slaEndTime,
                    slaWeekendEnabled, weekendShiftHc, skeletonRatio);
        }
    }

    /** Shift response. */
    public record ShiftView(
            UUID id, short shiftNo, LocalTime startTime, BigDecimal durationMinutes,
            BigDecimal headcount, boolean worksOnWeekend) {
    }

    /** Shift request row. */
    public record ShiftRequest(
            short shiftNo, @NotNull LocalTime startTime, BigDecimal durationMinutes,
            @NotNull BigDecimal headcount, boolean worksOnWeekend) {
    }

    /** Support item response. */
    public record SupportItemView(
            UUID id, UUID lineageId, String category, String activity, String frequencyCode,
            BigDecimal volume, String unitOfMeasure, BigDecimal workloadPerUnitMinutes,
            BigDecimal annualMultiplier, BigDecimal workloadPerYearHours, BigDecimal supportFte,
            String comments, String calculationVersion) {
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
            String comments) {
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
            UUID id, String month, BigDecimal actualVolume, String sourceType, UUID importBatchId) {
    }

    /** Monthly volume request row. */
    public record MonthlyVolumeRequest(@NotBlank String month, BigDecimal actualVolume) {
    }

    /** Daily volume response. */
    public record DailyVolumeView(
            UUID id,
            LocalDate volumeDate,
            BigDecimal actualVolume,
            String sourceType,
            UUID importBatchId) {
    }

    /** Daily volume request row. */
    public record DailyVolumeRequest(@NotNull LocalDate volumeDate, BigDecimal actualVolume) {
    }

    /** Slot volume response. */
    public record SlotVolumeView(
            UUID id,
            Instant slotStartAt,
            Instant slotEndAt,
            BigDecimal actualVolume,
            String sourceType,
            UUID importBatchId) {
    }

    /** Slot volume request row. */
    public record SlotVolumeRequest(
            @NotNull Instant slotStartAt,
            @NotNull Instant slotEndAt,
            @NotNull BigDecimal actualVolume) {
    }
}
