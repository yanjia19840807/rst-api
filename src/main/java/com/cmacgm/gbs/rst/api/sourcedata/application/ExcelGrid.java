package com.cmacgm.gbs.rst.api.sourcedata.application;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * Shared xlsx header and cell reading for MyHR and SKPI.
 */
final class ExcelGrid {

    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("M/d/uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT));

    private ExcelGrid() {
    }

    static Workbook open(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IOException("File is empty.");
        }
        return new XSSFWorkbook(inputStream);
    }

    static Sheet sheet(Workbook workbook, String name) {
        if (workbook == null || name == null) {
            return null;
        }
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            Sheet sheet = workbook.getSheetAt(index);
            if (sheet != null && name.equalsIgnoreCase(sheet.getSheetName().trim())) {
                return sheet;
            }
        }
        return null;
    }

    static Sheet firstSheet(Workbook workbook) {
        if (workbook == null || workbook.getNumberOfSheets() == 0) {
            return null;
        }
        return workbook.getSheetAt(0);
    }

    static Map<String, Integer> headers(Row row, DataFormatter formatter) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        if (row == null) {
            return headers;
        }
        for (Cell cell : row) {
            String key = headerKey(formatter.formatCellValue(cell));
            if (key != null) {
                headers.putIfAbsent(key, cell.getColumnIndex());
            }
        }
        return headers;
    }

    static void require(Map<String, Integer> headers, String... names) {
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            if (!headers.containsKey(name)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.INVALID_HEADER.code(),
                    "Missing column: " + String.join(", ", missing));
        }
    }

    static String text(Row row, Map<String, Integer> headers, String name, DataFormatter formatter) {
        Integer index = headers.get(name);
        if (index == null || row == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        String value = formatter.formatCellValue(cell).trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) {
            return null;
        }
        if (value.matches("\\d+\\.0+")) {
            return value.substring(0, value.indexOf('.'));
        }
        return value;
    }

    static LocalDate date(Row row, Map<String, Integer> headers, String name, DataFormatter formatter) {
        Integer index = headers.get(name);
        if (index == null || row == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = formatter.formatCellValue(cell).trim();
        if (value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter format : DATES) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try the next pattern
            }
        }
        return null;
    }

    static boolean rowBlank(Row row, Map<String, Integer> headers, DataFormatter formatter, String... names) {
        for (String name : names) {
            if (text(row, headers, name, formatter) != null) {
                return false;
            }
        }
        return true;
    }

    static ApiException missingSheet(String name) {
        return new ApiException(
                HttpStatus.CONFLICT,
                TimesheetSyncErrorCode.INVALID_HEADER.code(),
                "Missing sheet: " + name);
    }

    static ApiException unreadable(Exception ex) {
        return new ApiException(
                HttpStatus.CONFLICT,
                TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                "Unable to read file: " + ex.getMessage());
    }

    private static String headerKey(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace("\uFEFF", "").trim().replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text;
    }
}
