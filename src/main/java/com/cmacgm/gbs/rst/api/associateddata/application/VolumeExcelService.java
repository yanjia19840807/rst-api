package com.cmacgm.gbs.rst.api.associateddata.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.associateddata.api.dto.DailyVolumeRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.MonthlyVolumeRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SlotVolumeRequest;
import com.cmacgm.gbs.rst.api.common.error.ApiException;

/**
 * Excel import/export for Exercise volume input (monthly / daily / per-slot).
 */
@Component
public class VolumeExcelService {

    private static final List<String> MONTHLY_HEADERS = List.of("month", "actual_volume");
    private static final List<String> DAILY_HEADERS = List.of("date", "actual_volume");
    private static final List<String> SLOT_HEADERS = List.of("slot_start", "actual_volume");
    private static final long SLOT_MINUTES = 30;
    private static final String SLOT_EXCEL_DATE_FORMAT = "yyyy-mm-dd hh:mm";
    private static final List<DateTimeFormatter> SLOT_START_TEXT_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm[:ss]"),
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm[:ss]"),
            DateTimeFormatter.ofPattern("M/d/yy H:mm[:ss]"),
            new DateTimeFormatterBuilder()
                    .appendPattern("dd-MMM-yyyy[ HH:mm[:ss]]")
                    .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                    .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                    .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                    .toFormatter(Locale.ENGLISH));

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
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Per-slot");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("slot_start");
            header.createCell(1).setCellValue("actual_volume");
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat(SLOT_EXCEL_DATE_FORMAT));
            int rowIdx = 1;
            for (SlotVolumeRequest row : rows) {
                Row excelRow = sheet.createRow(rowIdx++);
                Cell startCell = excelRow.createCell(0);
                startCell.setCellValue(LocalDateTime.ofInstant(row.slotStartAt(), ZoneOffset.UTC));
                startCell.setCellStyle(dateStyle);
                excelRow.createCell(1).setCellValue(
                        row.actualVolume() == null ? 0d : row.actualVolume().doubleValue());
            }
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw conflict("excel-export-failed", "Unable to export volume Excel: " + ex.getMessage());
        }
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
            out.add(new MonthlyVolumeRequest(month.trim(), parseDecimal(row.get("actual_volume"), i)));
        }
        return out;
    }

    public List<DailyVolumeRequest> parseDaily(InputStream inputStream) {
        List<Map<String, String>> rows = parseRows(inputStream, DAILY_HEADERS);
        List<DailyVolumeRequest> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            LocalDate date = parseDate(row.get("date"), i);
            out.add(new DailyVolumeRequest(date, parseDecimal(row.get("actual_volume"), i)));
        }
        return out;
    }

    public List<SlotVolumeRequest> parseSlot(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw conflict("invalid-excel", "Workbook has no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = readHeaders(sheet.getRow(0), SLOT_HEADERS);
            List<SlotVolumeRequest> out = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row excelRow = sheet.getRow(i);
                if (excelRow == null || isBlank(excelRow, headers)) {
                    continue;
                }
                Instant startAt = readSlotStart(excelRow.getCell(headers.get("slot_start")), i);
                Instant endAt = startAt.plusSeconds(SLOT_MINUTES * 60L);
                BigDecimal volume = parseDecimal(
                        cell(excelRow, headers.get("actual_volume")), i - 1);
                out.add(new SlotVolumeRequest(
                        startAt,
                        endAt,
                        volume == null ? BigDecimal.ZERO : volume));
            }
            return out;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw conflict("invalid-excel", "Unable to read volume Excel: " + ex.getMessage());
        }
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

    private Instant readSlotStart(Cell cell, int sheetRowIndex) {
        int displayRow = sheetRowIndex + 1;
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw conflict("invalid-excel", "Row " + displayRow + ": slot_start is required.");
        }
        if (cell.getCellType() == CellType.NUMERIC
                && (DateUtil.isCellDateFormatted(cell)
                        || DateUtil.isValidExcelDate(cell.getNumericCellValue()))) {
            return cell.getLocalDateTimeCellValue().toInstant(ZoneOffset.UTC);
        }
        if (cell.getCellType() == CellType.FORMULA
                && cell.getCachedFormulaResultType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toInstant(ZoneOffset.UTC);
        }
        String raw = formatter.formatCellValue(cell).trim();
        if (raw.isBlank()) {
            throw conflict("invalid-excel", "Row " + displayRow + ": slot_start is required.");
        }
        Instant parsed = parseSlotStartText(raw);
        if (parsed != null) {
            return parsed;
        }
        throw conflict(
                "invalid-excel",
                "Row " + displayRow + ": slot_start must be a valid Excel date/time.");
    }

    private static Instant parseSlotStartText(String raw) {
        String value = raw.trim();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        String normalized = value.contains("T") ? value : value.replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        for (DateTimeFormatter format : SLOT_START_TEXT_FORMATS) {
            try {
                return LocalDateTime.parse(value, format).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
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
