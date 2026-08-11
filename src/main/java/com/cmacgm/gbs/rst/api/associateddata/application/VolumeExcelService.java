package com.cmacgm.gbs.rst.api.associateddata.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.associateddata.application.AssociatedDataService.DailyVolumeRequest;
import com.cmacgm.gbs.rst.api.associateddata.application.AssociatedDataService.MonthlyVolumeRequest;
import com.cmacgm.gbs.rst.api.associateddata.application.AssociatedDataService.SlotVolumeRequest;
import com.cmacgm.gbs.rst.api.common.error.ApiException;

/**
 * Excel import/export for Exercise volume input (monthly / daily / per-slot).
 */
@Component
public class VolumeExcelService {

    private static final List<String> MONTHLY_HEADERS = List.of("month", "actual_volume");
    private static final List<String> DAILY_HEADERS = List.of("date", "actual_volume");
    private static final List<String> SLOT_HEADERS =
            List.of("date", "slot_start", "slot_end", "actual_volume");

    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    public byte[] exportMonthlyBlank() {
        return writeSheet("Monthly", MONTHLY_HEADERS, List.of());
    }

    public byte[] exportDailyBlank() {
        return writeSheet("Daily", DAILY_HEADERS, List.of());
    }

    public byte[] exportSlotBlank() {
        return writeSheet("Per-slot", SLOT_HEADERS, List.of());
    }

    public byte[] exportMonthly(List<MonthlyVolumeRequest> rows) {
        List<List<String>> body = new ArrayList<>();
        for (MonthlyVolumeRequest row : rows) {
            body.add(List.of(
                    row.month(),
                    row.actualVolume() == null ? "" : row.actualVolume().toPlainString()));
        }
        return writeSheet("Monthly", MONTHLY_HEADERS, body);
    }

    public byte[] exportDaily(List<DailyVolumeRequest> rows) {
        List<List<String>> body = new ArrayList<>();
        for (DailyVolumeRequest row : rows) {
            body.add(List.of(
                    row.volumeDate().toString(),
                    row.actualVolume() == null ? "" : row.actualVolume().toPlainString()));
        }
        return writeSheet("Daily", DAILY_HEADERS, body);
    }

    public byte[] exportSlot(List<SlotVolumeRequest> rows) {
        List<List<String>> body = new ArrayList<>();
        for (SlotVolumeRequest row : rows) {
            LocalDate date = LocalDate.ofInstant(row.slotStartAt(), ZoneOffset.UTC);
            LocalTime start = LocalTime.ofInstant(row.slotStartAt(), ZoneOffset.UTC);
            LocalTime end = LocalTime.ofInstant(row.slotEndAt(), ZoneOffset.UTC);
            body.add(List.of(
                    date.toString(),
                    start.toString(),
                    end.toString(),
                    row.rawVolume() == null ? "0" : row.rawVolume().toPlainString()));
        }
        return writeSheet("Per-slot", SLOT_HEADERS, body);
    }

    public List<MonthlyVolumeRequest> parseMonthly(InputStream inputStream) {
        List<Map<String, String>> rows = parseRows(inputStream, MONTHLY_HEADERS);
        List<MonthlyVolumeRequest> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String month = row.get("month");
            if (month == null || month.isBlank()) {
                throw conflict("invalid-excel", "Row " + (i + 2) + ": month is required.");
            }
            out.add(new MonthlyVolumeRequest(month.trim(), parseDecimal(row.get("actual_volume"), i), null, null));
        }
        return out;
    }

    public List<DailyVolumeRequest> parseDaily(InputStream inputStream) {
        List<Map<String, String>> rows = parseRows(inputStream, DAILY_HEADERS);
        List<DailyVolumeRequest> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            LocalDate date = parseDate(row.get("date"), i);
            out.add(new DailyVolumeRequest(date, parseDecimal(row.get("actual_volume"), i), null, null));
        }
        return out;
    }

    public List<SlotVolumeRequest> parseSlot(InputStream inputStream, String defaultTimezone) {
        List<Map<String, String>> rows = parseRows(inputStream, SLOT_HEADERS);
        List<SlotVolumeRequest> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            LocalDate date = parseDate(row.get("date"), i);
            LocalTime start = parseTime(row.get("slot_start"), i, "slot_start");
            LocalTime end = parseTime(row.get("slot_end"), i, "slot_end");
            Instant startAt = date.atTime(start).toInstant(ZoneOffset.UTC);
            Instant endAt = date.atTime(end).toInstant(ZoneOffset.UTC);
            if (end.equals(LocalTime.MIDNIGHT) && start.isAfter(end)) {
                endAt = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            }
            BigDecimal volume = parseDecimal(row.get("actual_volume"), i);
            out.add(new SlotVolumeRequest(
                    startAt,
                    endAt,
                    volume == null ? BigDecimal.ZERO : volume,
                    defaultTimezone == null || defaultTimezone.isBlank() ? "Asia/Shanghai" : defaultTimezone));
        }
        return out;
    }

    private List<Map<String, String>> parseRows(InputStream inputStream, List<String> requiredHeaders) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw conflict("invalid-excel", "Workbook has no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = readHeaders(sheet.getRow(0), requiredHeaders);
            List<Map<String, String>> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row excelRow = sheet.getRow(i);
                if (excelRow == null || isBlank(excelRow, headers)) {
                    continue;
                }
                Map<String, String> values = new HashMap<>();
                for (String header : requiredHeaders) {
                    values.put(header, cell(excelRow, headers.get(header)));
                }
                rows.add(values);
            }
            return rows;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw conflict("invalid-excel", "Unable to read volume Excel: " + ex.getMessage());
        }
    }

    private byte[] writeSheet(String sheetName, List<String> headers, List<List<String>> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            int rowIdx = 1;
            for (List<String> values : rows) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < values.size(); i++) {
                    row.createCell(i).setCellValue(values.get(i));
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw conflict("excel-export-failed", "Unable to export volume Excel: " + ex.getMessage());
        }
    }

    private Map<String, Integer> readHeaders(Row headerRow, List<String> required) {
        if (headerRow == null) {
            throw conflict("invalid-excel", "Missing header row.");
        }
        Map<String, Integer> headers = new HashMap<>();
        for (Cell cell : headerRow) {
            String value = formatter.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
            if (!value.isBlank()) {
                headers.put(value, cell.getColumnIndex());
            }
        }
        for (String name : required) {
            if (!headers.containsKey(name)) {
                throw conflict("invalid-excel", "Missing required column: " + name + ".");
            }
        }
        return headers;
    }

    private boolean isBlank(Row row, Map<String, Integer> headers) {
        for (Integer index : headers.values()) {
            if (!cell(row, index).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String cell(Row row, Integer index) {
        if (index == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(index)).trim();
    }

    private LocalDate parseDate(String raw, int index) {
        if (raw == null || raw.isBlank()) {
            throw conflict("invalid-excel", "Row " + (index + 2) + ": date is required (YYYY-MM-DD).");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw conflict("invalid-excel", "Row " + (index + 2) + ": date must be YYYY-MM-DD.");
        }
    }

    private LocalTime parseTime(String raw, int index, String field) {
        if (raw == null || raw.isBlank()) {
            throw conflict("invalid-excel", "Row " + (index + 2) + ": " + field + " is required (HH:mm).");
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw conflict("invalid-excel", "Row " + (index + 2) + ": " + field + " must be HH:mm or HH:mm:ss.");
        }
    }

    private BigDecimal parseDecimal(String raw, int index) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim().replace(",", ""));
        } catch (NumberFormatException ex) {
            throw conflict("invalid-excel", "Row " + (index + 2) + ": actual_volume must be numeric.");
        }
    }

    private static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }
}
