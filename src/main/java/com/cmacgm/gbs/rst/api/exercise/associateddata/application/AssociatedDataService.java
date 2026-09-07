package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

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

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.DataImportBatch;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseHoliday;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup.TeamSetupInput;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseProductionSupportItem;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.SupportWorkloadMath;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeDailyInput;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeMonthlyInput;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeSlotInput;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.FileArtifact;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.DataImportBatchRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseHolidayRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseTeamSetupRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseProductionSupportItemRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeDailyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeMonthlyInputRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.ExerciseVolumeSlotInputRepository;
import com.cmacgm.gbs.rst.api.exercise.associateddata.persistence.FileArtifactRepository;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.exercise.cycletime.domain.CycleTimeBaseline;
import com.cmacgm.gbs.rst.api.exercise.cycletime.persistence.CycleTimeBaselineRepository;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseAccess;
import com.cmacgm.gbs.rst.api.exercise.domain.ExerciseSharedKpiLine;
import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.exercise.persistence.RstExerciseRepository;
import com.cmacgm.gbs.rst.api.exercise.scenario.application.ScenarioCommitService;
import com.cmacgm.gbs.rst.api.common.workingdays.WeekendCode;
import com.cmacgm.gbs.rst.api.supportcategory.application.SupportCategoryService;
import com.cmacgm.gbs.rst.api.supportcategory.application.SupportCategoryService.ResolvedCategory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.CalendarRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.CalendarView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.DailyVolumeRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.DailyVolumeView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.HolidayRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.HolidayView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.MonthlyVolumeRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.MonthlyVolumeView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SlotImportPreviewView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SlotImportResult;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SlotVolumeRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.VolumeSeriesImportPreviewView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SlotVolumeView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SupportItemRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SupportItemView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.TeamSetupRequest;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.TeamSetupView;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitVolumePointsView;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitVolumeSummaryView;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitVolumeService;

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
    private final SupportCategoryService supportCategories;
    private final CycleTimeBaselineRepository cycleTimeBaselines;
    private final VolumeInputValidator volumeValidator;
    private final VolumeExcelService volumeExcel;
    private final HolidayExcelService holidayExcel;
    private final SupportExcelService supportExcel;
    private final ImportTemplateService importTemplates;
    private final ToolkitVolumeService toolkitVolumes;
    private final FileArtifactRepository fileArtifacts;
    private final DataImportBatchRepository importBatches;
    private final RstExerciseRepository exerciseRepository;
    private final ScenarioCommitService scenarioCommits;
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
            SupportCategoryService supportCategories,
            CycleTimeBaselineRepository cycleTimeBaselines,
            VolumeInputValidator volumeValidator,
            VolumeExcelService volumeExcel,
            HolidayExcelService holidayExcel,
            SupportExcelService supportExcel,
            ImportTemplateService importTemplates,
            ToolkitVolumeService toolkitVolumes,
            FileArtifactRepository fileArtifacts,
            DataImportBatchRepository importBatches,
            RstExerciseRepository exerciseRepository,
            ScenarioCommitService scenarioCommits,
            Clock clock) {
        this.exercises = exercises;
        this.teamSetups = teamSetups;
        this.supportItems = supportItems;
        this.holidays = holidays;
        this.monthlyVolumes = monthlyVolumes;
        this.dailyVolumes = dailyVolumes;
        this.slotVolumes = slotVolumes;
        this.workingDaysService = workingDaysService;
        this.supportCategories = supportCategories;
        this.cycleTimeBaselines = cycleTimeBaselines;
        this.volumeValidator = volumeValidator;
        this.volumeExcel = volumeExcel;
        this.holidayExcel = holidayExcel;
        this.supportExcel = supportExcel;
        this.importTemplates = importTemplates;
        this.toolkitVolumes = toolkitVolumes;
        this.fileArtifacts = fileArtifacts;
        this.importBatches = importBatches;
        this.exerciseRepository = exerciseRepository;
        this.scenarioCommits = scenarioCommits;
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
        try {
            setup.replaceInputs(request.toInput(), ownerCcgid, now);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "invalid-weekend-code", ex.getMessage());
        }
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
        requireValidFrequency(request.frequencyCode());
        ResolvedCategory category = supportCategories.resolveForWrite(request.categoryId(), null);
        ExerciseProductionSupportItem item = ExerciseProductionSupportItem.create(
                exerciseId,
                category.categoryId(),
                category.categoryName(),
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
        requireValidFrequency(request.frequencyCode());
        ResolvedCategory category = supportCategories.resolveForWrite(
                request.categoryId(), item.getCategoryId());
        item.update(
                category.categoryId(),
                category.categoryName(),
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
     * Downloads the Production Support Excel template.
     */
    @Transactional(readOnly = true)
    public byte[] exportSupportTemplate(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        return importTemplates.download(ImportTemplateService.Kind.SUPPORT);
    }

    /**
     * Exports the current Production Support registry as Excel.
     */
    @Transactional(readOnly = true)
    public byte[] exportSupportExcel(String ownerCcgid, UUID exerciseId) {
        return supportExcel.export(listSupport(ownerCcgid, exerciseId));
    }

    /**
     * Upserts Production Support rows from Excel. Existing rows not in the file are kept.
     */
    @Transactional
    public List<SupportItemView> importSupportExcel(
            String ownerCcgid, UUID exerciseId, InputStream input, String fileName) {
        editable(ownerCcgid, exerciseId);
        List<SupportExcelService.ParsedRow> parsed = supportExcel.parse(input);
        if (parsed.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "invalid-excel", "No support rows found.");
        }
        Instant now = clock.instant();
        Map<String, ExerciseProductionSupportItem> existingByKey = new LinkedHashMap<>();
        for (ExerciseProductionSupportItem item :
                supportItems.findByExerciseIdAndDeletedAtIsNullOrderByCategoryAscActivityAsc(exerciseId)) {
            existingByKey.putIfAbsent(
                    itemKey(item.getCategoryId(), item.getActivity(), item.getFrequencyCode()),
                    item);
        }
        int accepted = 0;
        for (SupportExcelService.ParsedRow row : parsed) {
            ResolvedCategory category;
            try {
                category = supportCategories.resolveActiveByName(row.categoryName());
            } catch (ApiException ex) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "invalid-excel",
                        "Row " + row.displayRow() + ": " + ex.getMessage());
            }
            String key = itemKey(category.categoryId(), row.activity(), row.frequencyCode());
            ExerciseProductionSupportItem existing = existingByKey.get(key);
            if (existing == null) {
                ExerciseProductionSupportItem created = ExerciseProductionSupportItem.create(
                        exerciseId,
                        category.categoryId(),
                        category.categoryName(),
                        row.activity(),
                        row.frequencyCode(),
                        row.volume(),
                        row.unitOfMeasure(),
                        row.workloadPerUnitMinutes(),
                        blankToNull(row.comments()),
                        ownerCcgid,
                        now);
                supportItems.save(created);
                existingByKey.put(key, created);
            } else {
                existing.update(
                        category.categoryId(),
                        category.categoryName(),
                        row.activity(),
                        row.frequencyCode(),
                        row.volume(),
                        row.unitOfMeasure(),
                        row.workloadPerUnitMinutes(),
                        blankToNull(row.comments()),
                        ownerCcgid,
                        now);
                supportItems.save(existing);
            }
            accepted += 1;
        }
        recordImportBatch(
                ownerCcgid,
                exerciseId,
                "SUPPORT",
                fileName,
                accepted,
                "SUPPORT_IMPORT",
                "support-import.xlsx");
        return listSupport(ownerCcgid, exerciseId);
    }

    private static String itemKey(UUID categoryId, String activity, String frequencyCode) {
        String category = categoryId == null ? "" : categoryId.toString();
        return SupportExcelService.upsertKey(category, activity, frequencyCode);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
                if (holiday.holidayType() == null) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "invalid-holiday-type",
                            "Holiday type is required.");
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
                        holiday.holidayType(), ownerCcgid, now));
            }
        }
        return getCalendar(ownerCcgid, exerciseId);
    }

    /**
     * Downloads the holiday Excel template from SharePoint.
     */
    @Transactional(readOnly = true)
    public byte[] exportCalendarTemplate(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        return importTemplates.download(ImportTemplateService.Kind.CALENDAR);
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
     * Downloads the monthly volume Excel template from SharePoint.
     */
    @Transactional(readOnly = true)
    public byte[] exportMonthlyTemplate(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        return importTemplates.download(ImportTemplateService.Kind.VOLUME_MONTHLY);
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
     * Dry-run monthly Excel: validate, reset-from-Toolkit plan, no writes.
     */
    @Transactional(readOnly = true)
    public VolumeSeriesImportPreviewView previewMonthlyExcel(
            String ownerCcgid, UUID exerciseId, InputStream input) {
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        return planMonthlyImport(exercise, volumeExcel.parseMonthly(input)).preview();
    }

    /**
     * Imports monthly volumes: reset Exercise grid from Toolkit, then merge the file.
     */
    @Transactional
    public List<MonthlyVolumeView> importMonthlyExcel(
            String ownerCcgid, UUID exerciseId, InputStream input, String fileName) {
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        List<MonthlyVolumeRequest> parsed = volumeExcel.parseMonthly(input);
        MonthlyImportPlan plan = planMonthlyImport(exercise, parsed);
        UUID batchId = recordImportBatch(ownerCcgid, exerciseId, "MONTHLY_VOLUME", fileName, parsed.size());
        return replaceMonthlyVolumes(ownerCcgid, exerciseId, plan.merged(), "IMPORT", batchId);
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
     * Downloads the daily volume Excel template from SharePoint.
     */
    @Transactional(readOnly = true)
    public byte[] exportDailyTemplate(String ownerCcgid, UUID exerciseId) {
        exercises.requireOwned(ownerCcgid, exerciseId);
        return importTemplates.download(ImportTemplateService.Kind.VOLUME_DAILY);
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
     * Dry-run daily Excel: validate, reset-from-Toolkit plan, no writes.
     */
    @Transactional(readOnly = true)
    public VolumeSeriesImportPreviewView previewDailyExcel(
            String ownerCcgid, UUID exerciseId, InputStream input) {
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        return planDailyImport(exercise, volumeExcel.parseDaily(input)).preview();
    }

    /**
     * Imports daily volumes: reset Exercise grid from Toolkit, then merge the file.
     */
    @Transactional
    public List<DailyVolumeView> importDailyExcel(
            String ownerCcgid, UUID exerciseId, InputStream input, String fileName) {
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        List<DailyVolumeRequest> parsed = volumeExcel.parseDaily(input);
        DailyImportPlan plan = planDailyImport(exercise, parsed);
        UUID batchId = recordImportBatch(ownerCcgid, exerciseId, "DAILY_VOLUME", fileName, parsed.size());
        return replaceDailyVolumes(ownerCcgid, exerciseId, plan.merged(), "IMPORT", batchId);
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
     * Downloads the slot volume Excel template from SharePoint.
     */
    @Transactional(readOnly = true)
    public byte[] exportSlotTemplate(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        requireSlotPeriod(exercise);
        List<SlotVolumeRequest> rows = VolumeTrainWindows.slotTrainBounds(
                        exercise.getSlotStartDate(), exercise.getSlotWeeks())
                .stream()
                .map(bound -> new SlotVolumeRequest(bound.start(), bound.end(), null))
                .toList();
        return volumeExcel.exportSlot(rows);
    }

    /**
     * Exports current slot volumes as Excel.
     */
    @Transactional(readOnly = true)
    public byte[] exportSlotExcel(String ownerCcgid, UUID exerciseId) {
        RstExercise exercise = exercises.requireOwned(ownerCcgid, exerciseId);
        requireSlotPeriod(exercise);
        List<SlotVolumeRequest> rows = slotVolumes.findByExerciseIdOrderBySlotStartAtAsc(exerciseId).stream()
                .map(v -> new SlotVolumeRequest(
                        v.getSlotStartAt(), v.getSlotEndAt(), v.getActualVolume()))
                .toList();
        return volumeExcel.exportSlot(rows);
    }

    /**
     * Parses a Per-slot Excel file and infers Slot Period without writing.
     */
    @Transactional(readOnly = true)
    public SlotImportPreviewView previewSlotExcel(
            String ownerCcgid, UUID exerciseId, InputStream input) {
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        SlotImportPlan plan = volumeValidator.planSlotImport(volumeExcel.parseSlot(input));
        return new SlotImportPreviewView(
                plan.startDate(),
                plan.weeks(),
                plan.fileRowCount(),
                plan.paddedCount(),
                plan.totalSlots(),
                exercise.getSlotStartDate(),
                exercise.getSlotWeeks());
    }

    /**
     * Imports Per-slot Excel: infers Slot Period, replaces the grid, clears Slot Simulation only.
     */
    @Transactional
    public SlotImportResult importSlotExcel(
            String ownerCcgid, UUID exerciseId, InputStream input, String fileName) {
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        SlotImportPlan plan = volumeValidator.planSlotImport(volumeExcel.parseSlot(input));
        Instant now = clock.instant();
        exercise.updateSlotPeriod(plan.startDate(), plan.weeks(), ownerCcgid, now);
        exerciseRepository.saveAndFlush(exercise);
        UUID batchId = recordImportBatch(ownerCcgid, exerciseId, "SLOT_VOLUME", fileName, plan.fileRowCount());
        List<SlotVolumeView> volumes = replaceSlotVolumes(
                ownerCcgid, exerciseId, plan.grid(), "IMPORT", batchId);
        int cleared = scenarioCommits.clearSlotResultsForExercise(exerciseId);
        List<String> notices = new ArrayList<>();
        LocalDate end = VolumeTrainWindows.slotTrainEnd(plan.startDate(), plan.weeks());
        notices.add(
                "Per-slot Volume imported for "
                        + plan.startDate()
                        + " – "
                        + end
                        + " ("
                        + plan.weeks()
                        + (plan.weeks() == 1 ? " week)." : " weeks)."));
        if (plan.paddedCount() > 0) {
            notices.add("Filled " + plan.paddedCount() + " empty slots to complete the period.");
        }
        if (cleared > 0) {
            notices.add("Cleared saved Slot Simulation results for " + cleared + " scenario(s).");
        }
        return new SlotImportResult(
                plan.startDate(),
                plan.weeks(),
                plan.fileRowCount(),
                plan.paddedCount(),
                plan.totalSlots(),
                volumes,
                notices);
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
        RstExercise exercise = editable(ownerCcgid, exerciseId);
        requireSlotPeriod(exercise);
        volumeValidator.validateSlot(request);
        volumeValidator.validateSlotMatchesPeriod(
                request, exercise.getSlotStartDate(), exercise.getSlotWeeks());
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

    private MonthlyImportPlan planMonthlyImport(
            RstExercise exercise, List<MonthlyVolumeRequest> parsed) {
        LocalDate sizingMonth = exercise.getSizingMonth();
        volumeValidator.validateMonthlyImportRows(parsed, sizingMonth);
        Map<LocalDate, ToolkitVolumeService.VolumeSeed> seed =
                toolkitVolumes.monthlySeedByMonth(exercise.getToolkitId());
        List<YearMonth> toolkitMonths = new ArrayList<>();
        for (LocalDate month : seed.keySet()) {
            YearMonth ym = YearMonth.from(month);
            if (VolumeTrainWindows.monthlyInHistoryWindow(ym, sizingMonth)) {
                toolkitMonths.add(ym);
            }
        }
        List<YearMonth> fileMonths = parsed.stream()
                .map(row -> YearMonth.parse(row.month().trim()))
                .toList();
        volumeValidator.requireContinuousMonthUnion(fileMonths, toolkitMonths);
        Set<String> fileKeys = new HashSet<>();
        for (YearMonth month : fileMonths) {
            fileKeys.add(month.toString());
        }
        Set<String> toolkitKeys = new HashSet<>();
        for (YearMonth month : toolkitMonths) {
            toolkitKeys.add(month.toString());
        }
        return new MonthlyImportPlan(
                classifyKeys(fileKeys, toolkitKeys),
                mergeOntoToolkitGrid(parsed, ToolkitVolumeGrid.monthlyOverlay(seed, sizingMonth)),
                parsed.size());
    }

    private DailyImportPlan planDailyImport(
            RstExercise exercise, List<DailyVolumeRequest> parsed) {
        LocalDate sizingMonth = exercise.getSizingMonth();
        volumeValidator.validateDailyImportRows(parsed, sizingMonth);
        Map<LocalDate, ToolkitVolumeService.VolumeSeed> seed =
                toolkitVolumes.dailySeedByDate(exercise.getToolkitId());
        List<LocalDate> toolkitDates = seed.keySet().stream()
                .filter(date -> VolumeTrainWindows.dailyInHistoryWindow(date, sizingMonth))
                .toList();
        List<LocalDate> fileDates = parsed.stream().map(DailyVolumeRequest::volumeDate).toList();
        volumeValidator.requireContinuousDateUnion(fileDates, toolkitDates);
        Set<String> fileKeys = new HashSet<>();
        for (LocalDate date : fileDates) {
            fileKeys.add(date.toString());
        }
        Set<String> toolkitKeys = new HashSet<>();
        for (LocalDate date : toolkitDates) {
            toolkitKeys.add(date.toString());
        }
        return new DailyImportPlan(
                classifyKeys(fileKeys, toolkitKeys),
                mergeOntoToolkitDailyGrid(parsed, ToolkitVolumeGrid.dailyOverlay(seed, sizingMonth)),
                parsed.size());
    }

    private static OverlayKeys classifyKeys(Set<String> fileKeys, Set<String> toolkitKeys) {
        List<String> overwritten = fileKeys.stream().filter(toolkitKeys::contains).sorted().toList();
        List<String> added = fileKeys.stream().filter(key -> !toolkitKeys.contains(key)).sorted().toList();
        List<String> kept = toolkitKeys.stream().filter(key -> !fileKeys.contains(key)).sorted().toList();
        return new OverlayKeys(overwritten, added, kept);
    }

    private static List<MonthlyVolumeRequest> mergeOntoToolkitGrid(
            List<MonthlyVolumeRequest> parsed, List<MonthlyVolumeRequest> toolkitGrid) {
        Map<String, MonthlyVolumeRequest> byMonth = new LinkedHashMap<>();
        for (MonthlyVolumeRequest row : toolkitGrid) {
            byMonth.put(row.month(), row);
        }
        for (MonthlyVolumeRequest row : parsed) {
            String key = row.month().trim();
            MonthlyVolumeRequest existing = byMonth.get(key);
            BigDecimal commercial = row.commercialRatio() != null
                    ? row.commercialRatio()
                    : existing == null ? null : existing.commercialRatio();
            byMonth.put(key, new MonthlyVolumeRequest(key, row.actualVolume(), commercial));
        }
        return byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private static List<DailyVolumeRequest> mergeOntoToolkitDailyGrid(
            List<DailyVolumeRequest> parsed, List<DailyVolumeRequest> toolkitGrid) {
        Map<LocalDate, DailyVolumeRequest> byDate = new LinkedHashMap<>();
        for (DailyVolumeRequest row : toolkitGrid) {
            byDate.put(row.volumeDate(), row);
        }
        for (DailyVolumeRequest row : parsed) {
            DailyVolumeRequest existing = byDate.get(row.volumeDate());
            BigDecimal adjustment = row.dailyAdjustmentRatio() != null
                    ? row.dailyAdjustmentRatio()
                    : existing == null ? null : existing.dailyAdjustmentRatio();
            byDate.put(
                    row.volumeDate(),
                    new DailyVolumeRequest(row.volumeDate(), row.actualVolume(), adjustment));
        }
        return byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private record OverlayKeys(List<String> overwritten, List<String> added, List<String> kept) {
    }

    private record MonthlyImportPlan(
            OverlayKeys keys, List<MonthlyVolumeRequest> merged, int fileRowCount) {
        VolumeSeriesImportPreviewView preview() {
            return new VolumeSeriesImportPreviewView(
                    "MONTHLY", fileRowCount, keys.overwritten(), keys.added(), keys.kept());
        }
    }

    private record DailyImportPlan(
            OverlayKeys keys, List<DailyVolumeRequest> merged, int fileRowCount) {
        VolumeSeriesImportPreviewView preview() {
            return new VolumeSeriesImportPreviewView(
                    "DAILY", fileRowCount, keys.overwritten(), keys.added(), keys.kept());
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

    private static void requireSlotPeriod(RstExercise exercise) {
        if (!exercise.hasSlotPeriod()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "slot-period-required",
                    "Set a Slot Period to generate the per-slot grid.");
        }
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
        String weekend;
        try {
            weekend = WeekendCode.storedValue(setup.getWeekendCode());
        } catch (IllegalArgumentException ex) {
            weekend = setup.getWeekendCode();
        }
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

    private void requireValidFrequency(String frequencyCode) {
        try {
            SupportWorkloadMath.requireFrequency(frequencyCode);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "invalid-frequency", ex.getMessage());
        }
    }

    private SupportItemView toSupport(ExerciseProductionSupportItem item, UUID exerciseId) {
        ExerciseTeamSetup setup = teamSetups.findById(exerciseId).orElse(null);
        BigDecimal workingDays = workingDaysService.workingDaysPerYear(exerciseId);
        BigDecimal fteHours = SupportWorkloadMath.fteAnnualHours(setup, workingDays);
        SupportWorkloadMath.Derived derived;
        try {
            derived = SupportWorkloadMath.derive(item, workingDays, fteHours);
        } catch (IllegalArgumentException ex) {
            derived = new SupportWorkloadMath.Derived(null, null, null);
        }
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
