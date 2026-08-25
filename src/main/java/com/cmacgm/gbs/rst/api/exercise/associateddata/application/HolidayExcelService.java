package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.HolidayRequest;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.workingdays.HolidayDayKind;

/**
 * Excel import/export for Exercise holidays and makeup days (PH Dates).
 */
@Component
public class HolidayExcelService {

    private static final List<String> HEADERS = List.of("date", "type", "description");
    private static final List<String> REQUIRED = List.of("date", "type");
    private static final List<DateTimeFormatter> DATE_TEXT_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yy"),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));

    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    public byte[] exportBlank() {
        return writeSheet("Holidays", HEADERS, List.of());
    }

    public byte[] export(List<HolidayRequest> rows) {
        List<List<String>> body = new ArrayList<>();
        for (HolidayRequest row : rows) {
            body.add(List.of(
                    row.holidayDate() == null ? "" : row.holidayDate().toString(),
                    row.holidayType() == null ? "" : row.holidayType(),
                    row.holidayName() == null ? "" : row.holidayName()));
        }
        return writeSheet("Holidays", HEADERS, body);
    }

    public List<HolidayRequest> parse(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw conflict("invalid-excel", "Workbook has no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = readHeaders(sheet.getRow(0));
            List<HolidayRequest> out = new ArrayList<>();
            Set<LocalDate> seen = new HashSet<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row excelRow = sheet.getRow(i);
                if (excelRow == null || isBlank(excelRow, headers)) {
                    continue;
                }
                LocalDate date = readDate(excelRow.getCell(headers.get("date")), i);
                if (!seen.add(date)) {
                    throw conflict(
                            "invalid-excel",
                            "Row " + (i + 1) + ": each date can appear only once.");
                }
                String type = cell(excelRow, headers.get("type"));
                HolidayDayKind kind;
                try {
                    kind = HolidayDayKind.require(type);
                } catch (IllegalArgumentException ex) {
                    throw conflict("invalid-excel", "Row " + (i + 1) + ": " + ex.getMessage());
                }
                String description = headers.containsKey("description")
                        ? cell(excelRow, headers.get("description"))
                        : "";
                out.add(new HolidayRequest(date, description, kind.name()));
            }
            return out;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw conflict("invalid-excel", "Unable to read holiday Excel: " + ex.getMessage());
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
            throw conflict("excel-export-failed", "Unable to export holiday Excel: " + ex.getMessage());
        }
    }

    private Map<String, Integer> readHeaders(Row headerRow) {
        if (headerRow == null) {
            throw conflict("invalid-excel", "Missing header row.");
        }
        Map<String, Integer> headers = new HashMap<>();
        for (Cell cell : headerRow) {
            String value = normalizeHeader(formatter.formatCellValue(cell));
            if (!value.isBlank()) {
                headers.put(value, cell.getColumnIndex());
            }
        }
        for (String name : REQUIRED) {
            if (!headers.containsKey(name)) {
                throw conflict("invalid-excel", "Missing required column: " + name + ".");
            }
        }
        return headers;
    }

    private static String normalizeHeader(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (value) {
            case "holiday_date", "holidaydate" -> "date";
            case "holiday_type", "holidaytype", "day_type" -> "type";
            case "holiday_name", "holidayname", "name", "desc" -> "description";
            default -> value;
        };
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

    private LocalDate readDate(Cell cell, int sheetRowIndex) {
        int displayRow = sheetRowIndex + 1;
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw conflict("invalid-excel", "Row " + displayRow + ": date is required.");
        }
        if (cell.getCellType() == CellType.NUMERIC
                && (DateUtil.isCellDateFormatted(cell) || DateUtil.isValidExcelDate(cell.getNumericCellValue()))) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        if (cell.getCellType() == CellType.FORMULA
                && cell.getCachedFormulaResultType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String raw = formatter.formatCellValue(cell).trim();
        LocalDate parsed = parseDateText(raw);
        if (parsed != null) {
            return parsed;
        }
        throw conflict("invalid-excel", "Row " + displayRow + ": date must be a valid Excel date or YYYY-MM-DD.");
    }

    private static LocalDate parseDateText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            // try locale formats
        }
        for (DateTimeFormatter format : DATE_TEXT_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }
}
