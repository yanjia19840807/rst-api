package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * Parses Monthly Report Excel files into normalized Timesheet draft rows.
 */
@Component
public class TimesheetExcelParser {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "emp_ccgid",
            "emp_name",
            "emp_position_id",
            "supervisor_ccgid",
            "supervisor_name",
            "supervisor_position_id",
            "sr_manager_ccgid",
            "sr_manager_name",
            "sr_manager_position_id",
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
            "hc");

    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    /**
     * Parses and validates the workbook into draft rows ready for persistence.
     *
     * @param inputStream Excel binary stream
     * @param preferredSheet preferred sheet name; falls back to the first sheet
     * @return validated draft rows
     */
    public List<DraftRow> parse(InputStream inputStream, String preferredSheet) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = resolveSheet(workbook, preferredSheet);
            Map<String, Integer> headers = readHeaders(sheet.getRow(0));
            List<DraftRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row excelRow = sheet.getRow(i);
                if (excelRow == null || isBlankRow(excelRow, headers)) {
                    continue;
                }
                rows.add(mapRow(excelRow, headers, i + 1));
            }
            if (rows.isEmpty()) {
                throw conflict("INVALID_HEADER", "Timesheet file contains no data rows.");
            }
            validateHierarchy(rows);
            return rows;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw conflict("INVALID_HEADER", "Unable to read Timesheet Excel file: " + ex.getMessage());
        }
    }

    private Sheet resolveSheet(Workbook workbook, String preferredSheet) {
        if (preferredSheet != null && !preferredSheet.isBlank()) {
            Sheet named = workbook.getSheet(preferredSheet);
            if (named != null) {
                return named;
            }
        }
        if (workbook.getNumberOfSheets() == 0) {
            throw conflict("INVALID_HEADER", "Timesheet workbook has no sheets.");
        }
        return workbook.getSheetAt(0);
    }

    private Map<String, Integer> readHeaders(Row headerRow) {
        if (headerRow == null) {
            throw conflict("INVALID_HEADER", "Timesheet header row is missing.");
        }
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String name = normalizeHeader(formatter.formatCellValue(cell));
            if (name.isEmpty()) {
                continue;
            }
            if (headers.containsKey(name)) {
                throw conflict("INVALID_HEADER", "Duplicated Timesheet header: " + name);
            }
            headers.put(name, cell.getColumnIndex());
        }
        for (String required : REQUIRED_HEADERS) {
            if (!headers.containsKey(required)) {
                throw conflict("INVALID_HEADER", "Missing required Timesheet header: " + required);
            }
        }
        return headers;
    }

    private DraftRow mapRow(Row excelRow, Map<String, Integer> headers, int excelRowNumber) {
        String empCcgid = requireCcgid(cell(excelRow, headers, "emp_ccgid"), excelRowNumber, "emp_ccgid");
        String empName = requireText(cell(excelRow, headers, "emp_name"), excelRowNumber, "emp_name");
        String empPositionId = requireText(cell(excelRow, headers, "emp_position_id"), excelRowNumber, "emp_position_id");
        String supervisorCcgid = optionalCcgid(cell(excelRow, headers, "supervisor_ccgid"));
        String supervisorName = optionalText(cell(excelRow, headers, "supervisor_name"));
        String supervisorPositionId = optionalText(cell(excelRow, headers, "supervisor_position_id"));
        String srManagerCcgid = optionalCcgid(cell(excelRow, headers, "sr_manager_ccgid"));
        String srManagerName = optionalText(cell(excelRow, headers, "sr_manager_name"));
        String srManagerPositionId = optionalText(cell(excelRow, headers, "sr_manager_position_id"));
        String domainHeadCcgid = optionalCcgid(cell(excelRow, headers, "domain_head_ccgid"));
        String domainHeadName = optionalText(cell(excelRow, headers, "domain_head_name"));
        String domainHeadPositionId = optionalText(cell(excelRow, headers, "domain_head_position_id"));
        String center = requireText(cell(excelRow, headers, "center"), excelRowNumber, "center");
        String site = requireText(cell(excelRow, headers, "site"), excelRowNumber, "site");
        String domain = requireText(cell(excelRow, headers, "gbs_domain"), excelRowNumber, "gbs_domain");
        String pl1 = requireText(cell(excelRow, headers, "pl1"), excelRowNumber, "pl1");
        String pl2 = requireText(cell(excelRow, headers, "pl2"), excelRowNumber, "pl2");
        String pl3Code = requireText(cell(excelRow, headers, "pl3_code"), excelRowNumber, "pl3_code");
        String pl3Name = requireText(cell(excelRow, headers, "pl3"), excelRowNumber, "pl3");
        String carrier = optionalText(cell(excelRow, headers, "carrier"));
        String customerCountry = optionalText(cell(excelRow, headers, "customer_country"));
        BigDecimal hc = requireHc(cell(excelRow, headers, "hc"), excelRowNumber);

        return new DraftRow(
                empCcgid,
                empName,
                empPositionId,
                supervisorCcgid,
                supervisorName,
                supervisorPositionId,
                srManagerCcgid,
                srManagerName,
                srManagerPositionId,
                domainHeadCcgid,
                domainHeadName,
                domainHeadPositionId,
                center,
                site,
                domain,
                pl1,
                pl2,
                pl3Code,
                pl3Name,
                carrier,
                customerCountry,
                hc);
    }

    private void validateHierarchy(List<DraftRow> rows) {
        Map<String, Set<String>> empToSupervisor = new HashMap<>();
        Map<String, Set<String>> supervisorToManager = new HashMap<>();
        Map<String, Set<String>> managerToDomainHead = new HashMap<>();
        Map<String, Set<String>> empPosToSupPos = new HashMap<>();
        Map<String, Set<String>> supPosToMgrPos = new HashMap<>();
        Map<String, Set<String>> mgrPosToDhPos = new HashMap<>();

        for (DraftRow row : rows) {
            putEdge(empToSupervisor, row.empCcgid(), row.supervisorCcgid());
            putEdge(supervisorToManager, row.supervisorCcgid(), row.srManagerCcgid());
            putEdge(managerToDomainHead, row.srManagerCcgid(), row.domainHeadCcgid());
            putEdge(empPosToSupPos, row.empPositionId(), row.supervisorPositionId());
            putEdge(supPosToMgrPos, row.supervisorPositionId(), row.srManagerPositionId());
            putEdge(mgrPosToDhPos, row.srManagerPositionId(), row.domainHeadPositionId());
        }

        assertSingleParent(empToSupervisor, "emp_ccgid", "supervisor_ccgid");
        assertSingleParent(supervisorToManager, "supervisor_ccgid", "sr_manager_ccgid");
        assertSingleParent(managerToDomainHead, "sr_manager_ccgid", "domain_head_ccgid");
        assertSingleParent(empPosToSupPos, "emp_position_id", "supervisor_position_id");
        assertSingleParent(supPosToMgrPos, "supervisor_position_id", "sr_manager_position_id");
        assertSingleParent(mgrPosToDhPos, "sr_manager_position_id", "domain_head_position_id");
    }

    private static void putEdge(Map<String, Set<String>> edges, String child, String parent) {
        if (child == null || child.isBlank() || parent == null || parent.isBlank()) {
            return;
        }
        edges.computeIfAbsent(child, ignored -> new HashSet<>()).add(parent);
    }

    private static void assertSingleParent(
            Map<String, Set<String>> edges, String childLabel, String parentLabel) {
        for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw conflict(
                        "HIERARCHY_CONFLICT",
                        "One " + childLabel + " maps to multiple " + parentLabel
                                + " values (example child=" + entry.getKey() + ").");
            }
        }
    }

    private boolean isBlankRow(Row excelRow, Map<String, Integer> headers) {
        for (Integer index : headers.values()) {
            String value = optionalText(cell(excelRow, index));
            if (value != null) {
                return false;
            }
        }
        return true;
    }

    private String cell(Row row, Map<String, Integer> headers, String header) {
        Integer index = headers.get(header);
        if (index == null) {
            return null;
        }
        return cell(row, index);
    }

    private String cell(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private static String normalizeHeader(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, int rowNumber, String field) {
        if (value == null || value.isBlank()) {
            throw conflict("MISSING_SCOPE", "Row " + rowNumber + " missing required field: " + field);
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireCcgid(String value, int rowNumber, String field) {
        String normalized = optionalCcgid(value);
        if (normalized == null) {
            throw conflict("INVALID_CCGID", "Row " + rowNumber + " missing required CCGID: " + field);
        }
        return normalized;
    }

    private static String optionalCcgid(String value) {
        String text = optionalText(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    private static BigDecimal requireHc(String value, int rowNumber) {
        if (value == null || value.isBlank()) {
            throw conflict("INVALID_HC", "Row " + rowNumber + " missing hc.");
        }
        try {
            BigDecimal hc = new BigDecimal(value.replace(",", "")).setScale(6, RoundingMode.HALF_UP);
            if (hc.compareTo(BigDecimal.ZERO) < 0) {
                throw conflict("INVALID_HC", "Row " + rowNumber + " has negative hc.");
            }
            return hc;
        } catch (NumberFormatException ex) {
            throw conflict("INVALID_HC", "Row " + rowNumber + " has invalid hc: " + value);
        }
    }

    private static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    /**
     * Normalized Timesheet row prior to persistence.
     */
    public record DraftRow(
            String empCcgid,
            String empName,
            String empPositionId,
            String supervisorCcgid,
            String supervisorName,
            String supervisorPositionId,
            String srManagerCcgid,
            String srManagerName,
            String srManagerPositionId,
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
            BigDecimal hc) {
    }
}
