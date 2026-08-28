package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
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
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * Parses aligned Daily / Monthly Timesheet files (xlsx or csv).
 */
@Component
public class TimesheetReportParser {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "emp_id",
            "emp_ccgid",
            "emp_name",
            "supervisor_id",
            "supervisor_ccgid",
            "supervisor_name",
            "supervisor_position_id",
            "sr_manager_id",
            "sr_manager_ccgid",
            "sr_manager_name",
            "sr_manager_position_id",
            "domain_head_id",
            "domain_head_ccgid",
            "domain_head_name",
            "domain_head_position_id",
            "center",
            "site",
            "gbs_domain",
            "pl1",
            "pl2",
            "pl3_code",
            "pl3",
            "carrier",
            "customer_country",
            "hc");

    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    /**
     * Parses a workbook or CSV into report rows.
     *
     * @param inputStream file stream
     * @param fileName original file name
     * @return rows
     */
    public List<ReportRow> parse(InputStream inputStream, String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv")) {
                return parseCsv(inputStream);
            }
            return parseExcel(inputStream);
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw conflict(TimesheetSyncErrorCode.INVALID_HEADER, "Unable to read Timesheet file: " + ex.getMessage());
        }
    }

    private List<ReportRow> parseExcel(InputStream inputStream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw conflict(TimesheetSyncErrorCode.INVALID_HEADER, "Timesheet workbook has no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = readExcelHeaders(sheet.getRow(0));
            List<ReportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row excelRow = sheet.getRow(i);
                if (excelRow == null || isBlankExcel(excelRow, headers)) {
                    continue;
                }
                rows.add(mapExcel(excelRow, headers, i + 1));
            }
            if (rows.isEmpty()) {
                throw conflict(TimesheetSyncErrorCode.EMPTY_FILE, "Timesheet file contains no data rows.");
            }
            return rows;
        }
    }

    private List<ReportRow> parseCsv(InputStream inputStream) throws IOException {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw conflict(TimesheetSyncErrorCode.INVALID_HEADER, "Timesheet header row is missing.");
            }
            Map<String, Integer> headers = readCsvHeaders(splitCsv(headerLine));
            List<ReportRow> rows = new ArrayList<>();
            int rowNumber = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                List<String> cells = splitCsv(line);
                if (cells.stream().allMatch(value -> value == null || value.isBlank())) {
                    continue;
                }
                rows.add(mapCsv(cells, headers, rowNumber));
            }
            if (rows.isEmpty()) {
                throw conflict(TimesheetSyncErrorCode.EMPTY_FILE, "Timesheet file contains no data rows.");
            }
            return rows;
        }
    }

    private Map<String, Integer> readExcelHeaders(Row headerRow) {
        if (headerRow == null) {
            throw conflict(TimesheetSyncErrorCode.INVALID_HEADER, "Timesheet header row is missing.");
        }
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String name = normalizeHeader(formatter.formatCellValue(cell));
            if (name.isEmpty()) {
                continue;
            }
            if (headers.containsKey(name)) {
                throw conflict(TimesheetSyncErrorCode.INVALID_HEADER, "Duplicated Timesheet header: " + name);
            }
            headers.put(name, cell.getColumnIndex());
        }
        requireHeaders(headers);
        return headers;
    }

    private Map<String, Integer> readCsvHeaders(List<String> cells) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            String name = normalizeHeader(cells.get(i));
            if (name.isEmpty()) {
                continue;
            }
            if (headers.containsKey(name)) {
                throw conflict(TimesheetSyncErrorCode.INVALID_HEADER, "Duplicated Timesheet header: " + name);
            }
            headers.put(name, i);
        }
        requireHeaders(headers);
        return headers;
    }

    private void requireHeaders(Map<String, Integer> headers) {
        for (String required : REQUIRED_HEADERS) {
            if (!headers.containsKey(required)) {
                throw conflict(TimesheetSyncErrorCode.INVALID_HEADER, "Missing required Timesheet header: " + required);
            }
        }
        if (!headers.containsKey("date") && !headers.containsKey("month")) {
            throw conflict(TimesheetSyncErrorCode.INVALID_HEADER, "Missing required Timesheet header: date or month");
        }
    }

    private ReportRow mapExcel(Row excelRow, Map<String, Integer> headers, int rowNumber) {
        return new ReportRow(
                rowNumber,
                parseDate(excelValue(excelRow, headers, "date"), excelCell(excelRow, headers, "date")),
                parseMonth(excelValue(excelRow, headers, "month"), excelCell(excelRow, headers, "month")),
                asId(excelValue(excelRow, headers, "emp_id")),
                asCcgid(excelValue(excelRow, headers, "emp_ccgid")),
                optionalText(excelValue(excelRow, headers, "emp_name")),
                asId(excelValue(excelRow, headers, "emp_position_id")),
                asId(excelValue(excelRow, headers, "supervisor_id")),
                asCcgid(excelValue(excelRow, headers, "supervisor_ccgid")),
                optionalText(excelValue(excelRow, headers, "supervisor_name")),
                asId(excelValue(excelRow, headers, "supervisor_position_id")),
                asId(excelValue(excelRow, headers, "sr_manager_id")),
                asCcgid(excelValue(excelRow, headers, "sr_manager_ccgid")),
                optionalText(excelValue(excelRow, headers, "sr_manager_name")),
                asId(excelValue(excelRow, headers, "sr_manager_position_id")),
                asId(excelValue(excelRow, headers, "domain_head_id")),
                asCcgid(excelValue(excelRow, headers, "domain_head_ccgid")),
                optionalText(excelValue(excelRow, headers, "domain_head_name")),
                asId(excelValue(excelRow, headers, "domain_head_position_id")),
                optionalText(excelValue(excelRow, headers, "center")),
                optionalText(excelValue(excelRow, headers, "site")),
                optionalText(excelValue(excelRow, headers, "gbs_domain")),
                optionalText(excelValue(excelRow, headers, "pl1")),
                optionalText(excelValue(excelRow, headers, "pl2")),
                optionalText(excelValue(excelRow, headers, "pl3_code")),
                optionalText(excelValue(excelRow, headers, "pl3")),
                optionalText(excelValue(excelRow, headers, "carrier")),
                optionalText(excelValue(excelRow, headers, "customer_country")),
                parseHc(excelValue(excelRow, headers, "hc"), rowNumber),
                optionalText(excelValue(excelRow, headers, "management_or_production")),
                optionalText(excelValue(excelRow, headers, "cost_type")));
    }

    private ReportRow mapCsv(List<String> cells, Map<String, Integer> headers, int rowNumber) {
        return new ReportRow(
                rowNumber,
                parseDate(csvValue(cells, headers, "date"), null),
                parseMonth(csvValue(cells, headers, "month"), null),
                asId(csvValue(cells, headers, "emp_id")),
                asCcgid(csvValue(cells, headers, "emp_ccgid")),
                optionalText(csvValue(cells, headers, "emp_name")),
                asId(csvValue(cells, headers, "emp_position_id")),
                asId(csvValue(cells, headers, "supervisor_id")),
                asCcgid(csvValue(cells, headers, "supervisor_ccgid")),
                optionalText(csvValue(cells, headers, "supervisor_name")),
                asId(csvValue(cells, headers, "supervisor_position_id")),
                asId(csvValue(cells, headers, "sr_manager_id")),
                asCcgid(csvValue(cells, headers, "sr_manager_ccgid")),
                optionalText(csvValue(cells, headers, "sr_manager_name")),
                asId(csvValue(cells, headers, "sr_manager_position_id")),
                asId(csvValue(cells, headers, "domain_head_id")),
                asCcgid(csvValue(cells, headers, "domain_head_ccgid")),
                optionalText(csvValue(cells, headers, "domain_head_name")),
                asId(csvValue(cells, headers, "domain_head_position_id")),
                optionalText(csvValue(cells, headers, "center")),
                optionalText(csvValue(cells, headers, "site")),
                optionalText(csvValue(cells, headers, "gbs_domain")),
                optionalText(csvValue(cells, headers, "pl1")),
                optionalText(csvValue(cells, headers, "pl2")),
                optionalText(csvValue(cells, headers, "pl3_code")),
                optionalText(csvValue(cells, headers, "pl3")),
                optionalText(csvValue(cells, headers, "carrier")),
                optionalText(csvValue(cells, headers, "customer_country")),
                parseHc(csvValue(cells, headers, "hc"), rowNumber),
                optionalText(csvValue(cells, headers, "management_or_production")),
                optionalText(csvValue(cells, headers, "cost_type")));
    }

    private boolean isBlankExcel(Row excelRow, Map<String, Integer> headers) {
        for (Integer index : headers.values()) {
            if (optionalText(cell(excelRow, index)) != null) {
                return false;
            }
        }
        return true;
    }

    private String excelValue(Row row, Map<String, Integer> headers, String header) {
        Integer index = headers.get(header);
        return index == null ? null : cell(row, index);
    }

    private Cell excelCell(Row row, Map<String, Integer> headers, String header) {
        Integer index = headers.get(header);
        return index == null ? null : row.getCell(index);
    }

    private String csvValue(List<String> cells, Map<String, Integer> headers, String header) {
        Integer index = headers.get(header);
        if (index == null || index >= cells.size()) {
            return null;
        }
        return cells.get(index);
    }

    private String cell(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private LocalDate parseDate(String text, Cell cell) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        return parseIsoDate(text);
    }

    private LocalDate parseMonth(String text, Cell cell) {
        LocalDate date = parseDate(text, cell);
        return date == null ? parseYearMonth(text) : date.withDayOfMonth(1);
    }

    private static LocalDate parseIsoDate(String text) {
        String value = optionalText(text);
        if (value == null) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/uuuu"),
                DateTimeFormatter.ofPattern("uuuu/M/d"))) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        if (value.matches("\\d{5,6}")) {
            try {
                double serial = Double.parseDouble(value);
                return LocalDate.of(1899, 12, 30).plusDays((long) serial);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return parseYearMonth(value);
    }

    private static LocalDate parseYearMonth(String text) {
        String value = optionalText(text);
        if (value == null) {
            return null;
        }
        String compact = value.replace("-", "").replace("/", "");
        if (compact.matches("\\d{6}")) {
            int year = Integer.parseInt(compact.substring(0, 4));
            int month = Integer.parseInt(compact.substring(4, 6));
            return LocalDate.of(year, month, 1);
        }
        return null;
    }

    private HcValue parseHc(String text, int rowNumber) {
        String value = optionalText(text);
        if (value == null) {
            return new HcValue(null, false);
        }
        try {
            BigDecimal hc = new BigDecimal(value.replace(",", "")).setScale(6, RoundingMode.HALF_UP);
            if (hc.compareTo(BigDecimal.ZERO) < 0) {
                throw conflict(TimesheetSyncErrorCode.INVALID_HC, "Row " + rowNumber + " has negative hc.");
            }
            return new HcValue(hc, false);
        } catch (NumberFormatException ex) {
            throw conflict(TimesheetSyncErrorCode.INVALID_HC, "Row " + rowNumber + " has invalid hc: " + value);
        }
    }

    private static String asId(String value) {
        String text = optionalText(value);
        if (text == null) {
            return null;
        }
        if (text.matches("\\d+\\.0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }

    private static String asCcgid(String value) {
        String text = optionalText(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeHeader(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private static ApiException conflict(TimesheetSyncErrorCode code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code.code(), detail);
    }

    /**
     * One parsed Timesheet report row.
     */
    public record ReportRow(
            int sourceRow,
            LocalDate date,
            LocalDate month,
            String empId,
            String empCcgid,
            String empName,
            String empPositionId,
            String supervisorId,
            String supervisorCcgid,
            String supervisorName,
            String supervisorPositionId,
            String srManagerId,
            String srManagerCcgid,
            String srManagerName,
            String srManagerPositionId,
            String domainHeadId,
            String domainHeadCcgid,
            String domainHeadName,
            String domainHeadPositionId,
            String center,
            String site,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name,
            String carrier,
            String customerCountry,
            HcValue hc,
            String managementOrProduction,
            String costType) {
    }

    /**
     * Parsed HC.
     *
     * @param value numeric hc
     * @param invalid unused; invalid values throw
     */
    public record HcValue(BigDecimal value, boolean invalid) {
    }
}
