package com.cmacgm.gbs.rst.api.holidaytemplate.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.common.error.ApiException;

/**
 * Excel import/export for Center holiday template lines (date + name only).
 */
@Component
public class HolidayTemplateExcelService {

    private static final List<String> HEADERS = List.of("holiday_date", "holiday_name");

    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    public byte[] exportBlankTemplate() {
        return exportLines(List.of());
    }

    public byte[] exportLines(List<LineDraft> lines) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Holidays");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                header.createCell(i).setCellValue(HEADERS.get(i));
            }
            int rowIdx = 1;
            for (LineDraft line : lines) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(line.holidayDate().toString());
                row.createCell(1).setCellValue(line.holidayName());
            }
            for (int i = 0; i < HEADERS.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw conflict("excel-export-failed", "Unable to export holiday template Excel: " + ex.getMessage());
        }
    }

    public List<LineDraft> parse(InputStream inputStream, int expectedYear) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw conflict("invalid-excel", "Workbook has no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = readHeaders(sheet.getRow(0));
            List<LineDraft> rows = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row excelRow = sheet.getRow(i);
                if (excelRow == null || isBlank(excelRow, headers)) {
                    continue;
                }
                String dateRaw = cell(excelRow, headers.get("holiday_date"));
                String name = cell(excelRow, headers.get("holiday_name"));
                if (dateRaw.isBlank() || name.isBlank()) {
                    throw conflict("invalid-excel", "Row " + (i + 1) + ": holiday_date and holiday_name are required.");
                }
                LocalDate date;
                try {
                    date = LocalDate.parse(dateRaw.trim());
                } catch (DateTimeParseException ex) {
                    throw conflict("invalid-excel", "Row " + (i + 1) + ": holiday_date must be YYYY-MM-DD.");
                }
                if (date.getYear() != expectedYear) {
                    throw conflict(
                            "invalid-excel",
                            "Row " + (i + 1) + ": holiday_date year must be " + expectedYear + ".");
                }
                String key = date + "|" + name.trim().toLowerCase(Locale.ROOT);
                if (!keys.add(key)) {
                    throw conflict("invalid-excel", "Row " + (i + 1) + ": duplicate holiday_date + holiday_name.");
                }
                rows.add(new LineDraft(date, name.trim(), null));
            }
            return rows;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw conflict("invalid-excel", "Unable to read holiday Excel: " + ex.getMessage());
        }
    }

    private Map<String, Integer> readHeaders(Row headerRow) {
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
        for (String required : HEADERS) {
            if (!headers.containsKey(required)) {
                throw conflict("invalid-excel", "Missing required column: " + required);
            }
        }
        return headers;
    }

    private boolean isBlank(Row row, Map<String, Integer> headers) {
        return headers.values().stream().allMatch(idx -> cell(row, idx).isBlank());
    }

    private String cell(Row row, Integer idx) {
        if (idx == null) {
            return "";
        }
        Cell cell = row.getCell(idx);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public record LineDraft(LocalDate holidayDate, String holidayName, Boolean workingDayOverride) {
    }
}
