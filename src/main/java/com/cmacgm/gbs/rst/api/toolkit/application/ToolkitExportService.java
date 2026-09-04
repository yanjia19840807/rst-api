package com.cmacgm.gbs.rst.api.toolkit.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionRepository;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitHoliday;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitProductionSupportItem;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSharedKpiSelection;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSubtask;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitTeamSetup;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitVolumeDaily;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitVolumeMonthly;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitVolumeSlot;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a multi-sheet Excel export of Toolkit configuration and latest state.
 */
@Service
public class ToolkitExportService {

    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final ToolkitService toolkits;
    private final ToolkitAssociatedDataService associatedData;
    private final ToolkitVolumeService volumes;
    private final TmsSessionRepository tmsSessions;
    private final Clock clock;

    public ToolkitExportService(
            ToolkitService toolkits,
            ToolkitAssociatedDataService associatedData,
            ToolkitVolumeService volumes,
            TmsSessionRepository tmsSessions,
            Clock clock) {
        this.toolkits = toolkits;
        this.associatedData = associatedData;
        this.volumes = volumes;
        this.tmsSessions = tmsSessions;
        this.clock = clock;
    }

    /**
     * Exports one workbook for a managed Toolkit.
     */
    @Transactional(readOnly = true)
    public ExportFile export(String ccgid, UUID toolkitId) {
        Toolkit toolkit = toolkits.requireManaged(ccgid, toolkitId);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeToolkit(workbook, toolkit);
            writeSubtasks(workbook, toolkit);
            writeSharedKpi(workbook, toolkit);
            writeTeamSetup(workbook, associatedData.findTeamSetup(toolkitId).orElse(null));
            writeSupport(workbook, associatedData.listSupport(toolkitId));
            writeCalendar(workbook, associatedData.listHolidays(toolkitId));
            writeTms(workbook, tmsSessions.findByToolkit_IdAndStatusOrderByStartedAtAsc(
                    toolkitId, TmsSessionStatus.COMPLETED));
            writeMonthly(workbook, volumes.listMonthly(toolkitId));
            writeDaily(workbook, volumes.listDaily(toolkitId));
            writeSlot(workbook, volumes.listSlot(toolkitId));
            workbook.write(out);
            return new ExportFile(filename(toolkit.getName(), clock.instant()), out.toByteArray());
        } catch (IOException ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "toolkit-export-failed",
                    "Unable to export Toolkit Excel: " + ex.getMessage());
        }
    }

    private static void writeToolkit(Workbook workbook, Toolkit toolkit) {
        Sheet sheet = sheet(workbook, "Toolkit", List.of("field", "value"));
        add(sheet, "id", text(toolkit.getId()));
        add(sheet, "name", toolkit.getName());
        add(sheet, "description", toolkit.getDescription());
        add(sheet, "center", toolkit.getCenter());
        add(sheet, "domain", toolkit.getDomain());
        add(sheet, "pl1", toolkit.getPl1());
        add(sheet, "pl2", toolkit.getPl2());
        add(sheet, "pl3_code", toolkit.getPrimaryPl3Code());
        add(sheet, "pl3_name", toolkit.getPl3Name());
        add(sheet, "supervisor_position_id", toolkit.getSupervisorPositionId());
        add(sheet, "owner_ccgid", toolkit.getOwnerCcgid());
        add(sheet, "combine_subtasks_time", String.valueOf(toolkit.isCombineSubtasksTime()));
        autosize(sheet, 2);
    }

    private static void writeSubtasks(Workbook workbook, Toolkit toolkit) {
        Sheet sheet = sheet(workbook, "Subtasks", List.of(
                "name", "description", "display_order", "deleted"));
        int rowIdx = 1;
        for (ToolkitSubtask item : toolkit.getAllSubtasks()) {
            if (item.getDeletedAt() != null) {
                continue;
            }
            Row row = sheet.createRow(rowIdx++);
            cell(row, 0, item.getName());
            cell(row, 1, item.getDescription());
            cell(row, 2, String.valueOf(item.getDisplayOrder()));
            cell(row, 3, "false");
        }
        autosize(sheet, 4);
    }

    private static void writeSharedKpi(Workbook workbook, Toolkit toolkit) {
        Sheet sheet = sheet(workbook, "Shared KPI", List.of("carrier", "site", "customer_country"));
        int rowIdx = 1;
        for (ToolkitSharedKpiSelection item : toolkit.getSharedKpiSelections()) {
            if (item.getDeletedAt() != null) {
                continue;
            }
            Row row = sheet.createRow(rowIdx++);
            cell(row, 0, item.getCarrier());
            cell(row, 1, item.getSite());
            cell(row, 2, item.getCustomerCountry());
        }
        autosize(sheet, 3);
    }

    private static void writeTeamSetup(Workbook workbook, ToolkitTeamSetup setup) {
        Sheet sheet = sheet(workbook, "Team Setup", List.of("field", "value"));
        if (setup != null) {
            add(sheet, "source_exercise_id", text(setup.getSourceExerciseId()));
            add(sheet, "agents_lt_6m", decimal(setup.getAgentsLt6m()));
            add(sheet, "agents_6_24m", decimal(setup.getAgents6To24m()));
            add(sheet, "agents_24_48m", decimal(setup.getAgents24To48m()));
            add(sheet, "agents_gt_48m", decimal(setup.getAgentsGt48m()));
            add(sheet, "paid_leave_days", decimal(setup.getPaidLeaveDays()));
            add(sheet, "other_leave_days", decimal(setup.getOtherLeaveDays()));
            add(sheet, "availability_ratio", decimal(setup.getAvailabilityRatio()));
            add(sheet, "automation_ratio", decimal(setup.getAutomationRatio()));
            add(sheet, "max_overtime_minutes", decimal(setup.getMaxOvertimeMinutes()));
            add(sheet, "sla_type", setup.getSlaType());
            add(sheet, "sla_target_ratio", decimal(setup.getSlaTargetRatio()));
            add(sheet, "sla_turnaround_minutes", decimal(setup.getSlaTurnaroundMinutes()));
            add(sheet, "sla_start_time", setup.getSlaStartTime() == null ? "" : setup.getSlaStartTime().toString());
            add(sheet, "sla_end_time", setup.getSlaEndTime() == null ? "" : setup.getSlaEndTime().toString());
            add(sheet, "sla_weekend_enabled", setup.getSlaWeekendEnabled() == null
                    ? "" : String.valueOf(setup.getSlaWeekendEnabled()));
            add(sheet, "weekend_shift_hc", decimal(setup.getWeekendShiftHc()));
            add(sheet, "skeleton_ratio", decimal(setup.getSkeletonRatio()));
            add(sheet, "weekend_code", setup.getWeekendCode());
        }
        autosize(sheet, 2);
    }

    private static void writeSupport(Workbook workbook, List<ToolkitProductionSupportItem> items) {
        Sheet sheet = sheet(workbook, "Production Support", List.of(
                "category", "activity", "frequency_code", "volume", "unit_of_measure",
                "workload_per_unit_minutes", "comments"));
        int rowIdx = 1;
        for (ToolkitProductionSupportItem item : items) {
            Row row = sheet.createRow(rowIdx++);
            cell(row, 0, item.getCategory());
            cell(row, 1, item.getActivity());
            cell(row, 2, item.getFrequencyCode());
            cell(row, 3, decimal(item.getVolume()));
            cell(row, 4, item.getUnitOfMeasure());
            cell(row, 5, decimal(item.getWorkloadPerUnitMinutes()));
            cell(row, 6, item.getComments());
        }
        autosize(sheet, 7);
    }

    private static void writeCalendar(Workbook workbook, List<ToolkitHoliday> items) {
        Sheet sheet = sheet(workbook, "Calendar", List.of("holiday_date", "holiday_name", "holiday_type"));
        int rowIdx = 1;
        for (ToolkitHoliday item : items) {
            Row row = sheet.createRow(rowIdx++);
            cell(row, 0, date(item.getHolidayDate()));
            cell(row, 1, item.getHolidayName());
            cell(row, 2, item.getHolidayType() == null ? "" : item.getHolidayType().name());
        }
        autosize(sheet, 3);
    }

    private static void writeTms(Workbook workbook, List<TmsSession> sessions) {
        Sheet sheet = sheet(workbook, "TMS", List.of(
                "session_no", "agent_ccgid", "subtask", "status", "started_at", "ended_at",
                "net_duration_seconds", "processed_volume", "reference", "remarks"));
        int rowIdx = 1;
        for (TmsSession session : sessions) {
            Row row = sheet.createRow(rowIdx++);
            cell(row, 0, session.getSessionNo());
            cell(row, 1, session.getAgentCcgid());
            cell(row, 2, session.getToolkitSubtask() == null ? "" : session.getToolkitSubtask().getName());
            cell(row, 3, session.getStatus().name());
            cell(row, 4, instant(session.getStartedAt()));
            cell(row, 5, instant(session.getEndedAt()));
            cell(row, 6, String.valueOf(session.getNetDurationSeconds()));
            cell(row, 7, decimal(session.getProcessedVolume()));
            cell(row, 8, session.getReference());
            cell(row, 9, session.getRemarks());
        }
        autosize(sheet, 10);
    }

    private static void writeMonthly(Workbook workbook, List<ToolkitVolumeMonthly> rows) {
        Sheet sheet = sheet(workbook, "Monthly Volume", List.of(
                "month", "actual_volume", "commercial_ratio"));
        int rowIdx = 1;
        for (ToolkitVolumeMonthly row : rows) {
            Row excel = sheet.createRow(rowIdx++);
            cell(excel, 0, MonthKeys.formatYearMonth(row.getMonth()));
            cell(excel, 1, decimal(row.getActualVolume()));
            cell(excel, 2, decimal(row.getCommercialRatio()));
        }
        autosize(sheet, 3);
    }

    private static void writeDaily(Workbook workbook, List<ToolkitVolumeDaily> rows) {
        Sheet sheet = sheet(workbook, "Daily Volume", List.of(
                "date", "actual_volume", "daily_adjustment_ratio"));
        int rowIdx = 1;
        for (ToolkitVolumeDaily row : rows) {
            Row excel = sheet.createRow(rowIdx++);
            cell(excel, 0, date(row.getVolumeDate()));
            cell(excel, 1, decimal(row.getActualVolume()));
            cell(excel, 2, decimal(row.getDailyAdjustmentRatio()));
        }
        autosize(sheet, 3);
    }

    private static void writeSlot(Workbook workbook, List<ToolkitVolumeSlot> rows) {
        Sheet sheet = sheet(workbook, "Slot Volume", List.of(
                "slot_start", "slot_end", "actual_volume"));
        int rowIdx = 1;
        for (ToolkitVolumeSlot row : rows) {
            Row excel = sheet.createRow(rowIdx++);
            cell(excel, 0, instant(row.getSlotStartAt()));
            cell(excel, 1, instant(row.getSlotEndAt()));
            cell(excel, 2, decimal(row.getActualVolume()));
        }
        autosize(sheet, 3);
    }

    private static Sheet sheet(Workbook workbook, String name, List<String> headers) {
        Sheet sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            header.createCell(i).setCellValue(headers.get(i));
        }
        return sheet;
    }

    private static void add(Sheet sheet, String field, String value) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        cell(row, 0, field);
        cell(row, 1, value);
    }

    private static void cell(Row row, int index, String value) {
        row.createCell(index).setCellValue(value == null ? "" : value);
    }

    private static void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static String filename(String name, Instant now) {
        String safe = (name == null || name.isBlank() ? "toolkit" : name)
                .replaceAll("[^A-Za-z0-9._-]+", "_");
        String day = now.atZone(ZoneOffset.UTC).toLocalDate().toString().replace("-", "");
        return safe + "_export_" + day + ".xlsx";
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private static String instant(Instant value) {
        return value == null ? "" : ISO_INSTANT.format(value);
    }

    private static String text(UUID value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Generated workbook bytes and download name.
     */
    public record ExportFile(String filename, byte[] body) {
    }
}
