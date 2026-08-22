package com.cmacgm.gbs.rst.api.associateddata.application;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
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
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseTeamSetup.TeamSetupInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeDailyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeMonthlyInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeSlotInput;
import com.cmacgm.gbs.rst.api.associateddata.domain.FileArtifact;
import com.cmacgm.gbs.rst.api.associateddata.persistence.DataImportBatchRepository;
import com.cmacgm.gbs.rst.api.associateddata.persistence.ExerciseHolidayRepository;
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
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.WorkingDaysService;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.HolidayDayKind;
import com.cmacgm.gbs.rst.api.holidaytemplate.domain.WeekendCode;
import com.cmacgm.gbs.rst.api.supporttaxonomy.application.SupportTaxonomyService;
import com.cmacgm.gbs.rst.api.supporttaxonomy.application.SupportTaxonomyService.ResolvedCategory;
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
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SlotVolumeRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SlotVolumeView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SupportItemRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SupportItemView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.TeamSetupRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.TeamSetupView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ToolkitVolumePointsView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ToolkitVolumeSummaryView;

/**
 * Associated Data CRUD for Supervisor Exercise Team Setup, Support, Calendar and Volume.
 */
@Service
public class AssociatedDataService {

    private final ExerciseAccess exercises;
    private final ExerciseTeamSetupRepository teamSetups;
    private final ExerciseProductionSupportItemRepository supportItems;
    private final ExerciseHolidayRepository holidays;
    private final ExerciseVolumeMonthlyInputRepository monthlyVolumes;
    private final ExerciseVolumeDailyInputRepository dailyVolumes;
    private final ExerciseVolumeSlotInputRepository slotVolumes;
    private final WorkingDaysService workingDaysService;
    private final SupportTaxonomyService supportTaxonomy;
    private final CycleTimeBaselineRepository cycleTimeBaselines;
    private final VolumeInputValidator volumeValidator;
    private final VolumeExcelService volumeExcel;
    private final HolidayExcelService holidayExcel;
    private final ToolkitVolumeService toolkitVolumes;
    private final FileArtifactRepository fileArtifacts;
    private final DataImportBatchRepository importBatches;
    private final Clock clock;

    /**
     * Creates the Associated Data service.
     */
    public AssociatedDataService(
            ExerciseAccess exercises,
            ExerciseTeamSetupRepository teamSetups,
            ExerciseProductionSupportItemRepository supportItems,
            ExerciseHolidayRepository holidays,
            ExerciseVolumeMonthlyInputRepository monthlyVolumes,
            ExerciseVolumeDailyInputRepository dailyVolumes,
            ExerciseVolumeSlotInputRepository slotVolumes,
            WorkingDaysService workingDaysService,
            SupportTaxonomyService supportTaxonomy,
            CycleTimeBaselineRepository cycleTimeBaselines,
            VolumeInputValidator volumeValidator,
            VolumeExcelService volumeExcel,
            HolidayExcelService holidayExcel,
            ToolkitVolumeService toolkitVolumes,
            FileArtifactRepository fileArtifacts,
            DataImportBatchRepository importBatches,
            Clock clock) {
        this.exercises = exercises;
        this.teamSetups = teamSetups;
        this.supportItems = supportItems;
        this.holidays = holidays;
        this.monthlyVolumes = monthlyVolumes;
        this.dailyVolumes = dailyVolumes;
        this.slotVolumes = slotVolumes;
        this.workingDaysService = workingDaysService;
        this.supportTaxonomy = supportTaxonomy;
        this.cycleTimeBaselines = cycleTimeBaselines;
        this.volumeValidator = volumeValidator;
        this.volumeExcel = volumeExcel;
        this.holidayExcel = holidayExcel;
        this.toolkitVolumes = toolkitVolumes;
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
        ResolvedCategory taxonomy = supportTaxonomy.resolveForWrite(request.categoryId(), null);
        ExerciseProductionSupportItem item = ExerciseProductionSupportItem.create(
                exerciseId,
                taxonomy.categoryId(),
                taxonomy.categoryName(),
                request.activity(),
                request.frequencyCode(),
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
        ResolvedCategory taxonomy = supportTaxonomy.resolveForWrite(
                request.categoryId(), item.getCategoryId());
        item.update(
                taxonomy.categoryId(),
                taxonomy.categoryName(),
                request.activity(),
                request.frequencyCode(), request.volume(),
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
     * Returns active holidays.
     *
     * @param ownerCcgid Supervisor CCGID
     * @param exerciseId Exercise id
     * @return calendar view
     */
    @Transactional(readOnly = true)
    public CalendarView getCalendar(String ownerCcgid, UUID exerciseId) {
        exercises.requireReadable(ownerCcgid, exerciseId);
        List<HolidayView> holidayViews = holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId)
                .stream()
                .map(this::toHoliday)
                .toList();
        return new CalendarView(holidayViews);
    }

    /**
     * Replaces the holiday list (Excel PH Dates). Weekend code is stored on Team Setup.
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
        for (ExerciseHoliday existing : holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId)) {
            existing.softDelete(ownerCcgid, now);
            holidays.save(existing);
        }
        holidays.flush();
        if (request.holidays() != null) {
            Set<LocalDate> seen = new HashSet<>();
            for (HolidayRequest holiday : request.holidays()) {
                HolidayDayKind kind;
                try {
                    kind = HolidayDayKind.require(holiday.holidayType());
                } catch (IllegalArgumentException ex) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY, "invalid-holiday-type", ex.getMessage());
                }
                if (holiday.holidayDate() == null || !seen.add(holiday.holidayDate())) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "duplicate-holiday-date",
                            "Each date can appear only once in the holiday list.");
                }
                String name = holiday.holidayName() == null ? "" : holiday.holidayName().trim();
                holidays.save(ExerciseHoliday.create(
                        exerciseId, holiday.holidayDate(), name,
                        kind.name(), ownerCcgid, now));
            }
        }
        return getCalendar(ownerCcgid, exerciseId);
    }

    /**
     * Exports a blank holiday Excel template (date / type / description).
     */
    @Transactional(readOnly = true)
    public byte[] exportCalendarTemplate(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        return holidayExcel.exportBlank();
    }

    /**
     * Exports current holidays as Excel.
     */
    @Transactional(readOnly = true)
    public byte[] exportCalendarExcel(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        List<HolidayRequest> rows = holidays
                .findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(exerciseId)
                .stream()
                .map(h -> new HolidayRequest(h.getHolidayDate(), h.getHolidayName(), h.getHolidayType()))
                .toList();
        return holidayExcel.export(rows);
    }

    /**
     * Imports holidays from Excel and replaces the current list.
     */
    @Transactional
    public CalendarView importCalendarExcel(
            String ownerCcgid, UUID exerciseId, InputStream input, String fileName) {
        editable(ownerCcgid, exerciseId);
        List<HolidayRequest> parsed = holidayExcel.parse(input);
        if (parsed.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "invalid-excel", "No holiday rows found.");
        }
        recordImportBatch(
                ownerCcgid,
                exerciseId,
                "HOLIDAY",
                fileName,
                parsed.size(),
                "HOLIDAY_IMPORT",
                "holiday-import.xlsx");
        return putCalendar(ownerCcgid, exerciseId, new CalendarRequest(parsed));
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
                        v.getCommercialRatio(),
                        v.getSourceType(),
                        v.getImportBatchId()))
                .toList();
    }

    /**
     * Canonical Toolkit volume coverage used as the forecast training source.
     */
    @Transactional(readOnly = true)
    public ToolkitVolumeSummaryView getToolkitVolumeSummary(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.requireReadable(ownerCcgid, exerciseId);
        return toolkitVolumes.summarize(exercise.getToolkitId());
    }

    /**
     * Canonical Toolkit actuals for add-row / import pre-fill.
     */
    @Transactional(readOnly = true)
    public ToolkitVolumePointsView getToolkitVolumePoints(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.requireReadable(ownerCcgid, exerciseId);
        return toolkitVolumes.listPoints(exercise.getToolkitId());
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
                .map(v -> new MonthlyVolumeRequest(
                        MonthKeys.formatYearMonth(v.getMonth()),
                        v.getActualVolume(),
                        v.getCommercialRatio()))
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
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        volumeValidator.validateMonthlyImportRows(parsed, exercise.getSizingMonth());
        UUID batchId = recordImportBatch(ownerCcgid, exerciseId, "MONTHLY_VOLUME", fileName, parsed.size());
        List<MonthlyVolumeRequest> merged = mergeMonthly(exercise, parsed);
        return replaceMonthlyVolumes(ownerCcgid, exerciseId, merged, "IMPORT", batchId);
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
                        v.getDailyAdjustmentRatio(),
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
                .map(v -> new DailyVolumeRequest(
                        v.getVolumeDate(), v.getActualVolume(), v.getDailyAdjustmentRatio()))
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
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        volumeValidator.validateDailyImportRows(parsed, exercise.getSizingMonth());
        UUID batchId = recordImportBatch(ownerCcgid, exerciseId, "DAILY_VOLUME", fileName, parsed.size());
        List<DailyVolumeRequest> merged = mergeDaily(exercise, parsed);
        return replaceDailyVolumes(ownerCcgid, exerciseId, merged, "IMPORT", batchId);
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
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        volumeValidator.validateMonthlyForExercise(request, exercise.getSizingMonth());
        Instant now = clock.instant();
        monthlyVolumes.deleteByExerciseId(exerciseId);
        monthlyVolumes.flush();
        List<ExerciseVolumeMonthlyInput> rows = new ArrayList<>(request.size());
        for (MonthlyVolumeRequest row : request) {
            rows.add(ExerciseVolumeMonthlyInput.create(
                    exerciseId,
                    MonthKeys.parseMonthStart(row.month()),
                    row.actualVolume(),
                    row.commercialRatio(),
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
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        volumeValidator.validateDailyForExercise(request, exercise.getSizingMonth());
        Instant now = clock.instant();
        dailyVolumes.deleteByExerciseId(exerciseId);
        dailyVolumes.flush();
        List<ExerciseVolumeDailyInput> rows = new ArrayList<>(request.size());
        for (DailyVolumeRequest row : request) {
            rows.add(ExerciseVolumeDailyInput.create(
                    exerciseId,
                    row.volumeDate(),
                    row.actualVolume(),
                    row.dailyAdjustmentRatio(),
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

    private List<MonthlyVolumeRequest> mergeMonthly(
            RstExercise exercise, List<MonthlyVolumeRequest> parsed) {
        Map<String, MonthlyVolumeRequest> byMonth = new LinkedHashMap<>();
        for (ExerciseVolumeMonthlyInput row : monthlyVolumes.findByExerciseIdOrderByMonthAsc(exercise.getId())) {
            String key = MonthKeys.formatYearMonth(row.getMonth());
            byMonth.put(key, new MonthlyVolumeRequest(key, row.getActualVolume(), row.getCommercialRatio()));
        }
        Map<LocalDate, BigDecimal> seed = toolkitVolumes.monthlySeedByMonth(exercise.getToolkitId());
        for (MonthlyVolumeRequest row : parsed) {
            String key = row.month().trim();
            BigDecimal volume = row.actualVolume();
            BigDecimal commercial = row.commercialRatio();
            MonthlyVolumeRequest existing = byMonth.get(key);
            if (!byMonth.containsKey(key) && volume == null) {
                volume = seed.get(YearMonth.parse(key).atDay(1));
            }
            if (commercial == null && existing != null) {
                commercial = existing.commercialRatio();
            }
            byMonth.put(key, new MonthlyVolumeRequest(key, volume, commercial));
        }
        YearMonth cutoff = YearMonth.from(exercise.getSizingMonth());
        fillMonthlyGaps(byMonth, seed, cutoff);
        return byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private List<DailyVolumeRequest> mergeDaily(
            RstExercise exercise, List<DailyVolumeRequest> parsed) {
        Map<LocalDate, DailyVolumeRequest> byDate = new LinkedHashMap<>();
        for (ExerciseVolumeDailyInput row : dailyVolumes.findByExerciseIdOrderByVolumeDateAsc(exercise.getId())) {
            byDate.put(
                    row.getVolumeDate(),
                    new DailyVolumeRequest(
                            row.getVolumeDate(), row.getActualVolume(), row.getDailyAdjustmentRatio()));
        }
        Map<LocalDate, BigDecimal> seed = toolkitVolumes.dailySeedByDate(exercise.getToolkitId());
        for (DailyVolumeRequest row : parsed) {
            BigDecimal volume = row.actualVolume();
            BigDecimal adjustment = row.dailyAdjustmentRatio();
            DailyVolumeRequest existing = byDate.get(row.volumeDate());
            if (!byDate.containsKey(row.volumeDate()) && volume == null) {
                volume = seed.get(row.volumeDate());
            }
            if (adjustment == null && existing != null) {
                adjustment = existing.dailyAdjustmentRatio();
            }
            byDate.put(row.volumeDate(), new DailyVolumeRequest(row.volumeDate(), volume, adjustment));
        }
        LocalDate cutoff = YearMonth.from(exercise.getSizingMonth()).atEndOfMonth();
        fillDailyGaps(byDate, seed, cutoff);
        return byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private static void fillMonthlyGaps(
            Map<String, MonthlyVolumeRequest> byMonth,
            Map<LocalDate, BigDecimal> seed,
            YearMonth cutoff) {
        if (byMonth.isEmpty()) {
            return;
        }
        YearMonth min = byMonth.keySet().stream().map(YearMonth::parse).min(YearMonth::compareTo).orElse(cutoff);
        YearMonth max = byMonth.keySet().stream().map(YearMonth::parse).max(YearMonth::compareTo).orElse(cutoff);
        if (max.isAfter(cutoff)) {
            max = cutoff;
        }
        for (YearMonth ym = min; !ym.isAfter(max); ym = ym.plusMonths(1)) {
            String key = ym.toString();
            LocalDate monthStart = ym.atDay(1);
            byMonth.putIfAbsent(key, new MonthlyVolumeRequest(key, seed.get(monthStart)));
        }
    }

    private static void fillDailyGaps(
            Map<LocalDate, DailyVolumeRequest> byDate,
            Map<LocalDate, BigDecimal> seed,
            LocalDate cutoff) {
        if (byDate.isEmpty()) {
            return;
        }
        LocalDate min = byDate.keySet().stream().min(LocalDate::compareTo).orElse(cutoff);
        LocalDate max = byDate.keySet().stream().max(LocalDate::compareTo).orElse(cutoff);
        if (max.isAfter(cutoff)) {
            max = cutoff;
        }
        for (LocalDate date = min; !date.isAfter(max); date = date.plusDays(1)) {
            byDate.putIfAbsent(date, new DailyVolumeRequest(date, seed.get(date)));
        }
    }

    private UUID recordImportBatch(
            String ownerCcgid, UUID exerciseId, String importType, String fileName, int rowCount) {
        return recordImportBatch(
                ownerCcgid, exerciseId, importType, fileName, rowCount,
                "VOLUME_IMPORT", "volume-import.xlsx");
    }

    private UUID recordImportBatch(
            String ownerCcgid,
            UUID exerciseId,
            String importType,
            String fileName,
            int rowCount,
            String artifactType,
            String defaultFileName) {
        Instant now = clock.instant();
        FileArtifact artifact = fileArtifacts.save(FileArtifact.createStub(
                artifactType,
                "EXERCISE",
                exerciseId,
                fileName == null || fileName.isBlank() ? defaultFileName : fileName,
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

    private TeamSetupView toTeamSetup(RstExercise exercise, ExerciseTeamSetup setup) {
        BigDecimal workingDays = workingDaysService.workingDaysPerYear(exercise);
        BigDecimal cycleTime = activeCycleTimeSeconds(exercise.getId());
        String weekend = WeekendCode.storedValue(setup.getWeekendCode());
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
                setup.getVersion());
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
                    frequencyCode, workingDaysService.workingDaysPerYear(exerciseId));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "invalid-frequency", ex.getMessage());
        }
    }

    private SupportItemView toSupport(ExerciseProductionSupportItem item, UUID exerciseId) {
        ExerciseTeamSetup setup = teamSetups.findById(exerciseId).orElse(null);
        BigDecimal workingDays = workingDaysService.workingDaysPerYear(exerciseId);
        BigDecimal fteHours = SupportWorkloadMath.fteAnnualHours(setup, workingDays);
        SupportWorkloadMath.Derived derived = SupportWorkloadMath.derive(item, workingDays, fteHours);
        return new SupportItemView(
                item.getId(), item.getLineageId(), item.getCategoryId(), item.getCategory(),
                item.getActivity(),
                item.getFrequencyCode(), item.getVolume(), item.getUnitOfMeasure(),
                item.getWorkloadPerUnitMinutes(), derived.annualMultiplier(),
                derived.workloadPerYearHours(), derived.supportFte(), item.getComments());
    }

    private HolidayView toHoliday(ExerciseHoliday holiday) {
        return new HolidayView(
                holiday.getId(), holiday.getHolidayDate(), holiday.getHolidayName(),
                holiday.getHolidayType());
    }
}
