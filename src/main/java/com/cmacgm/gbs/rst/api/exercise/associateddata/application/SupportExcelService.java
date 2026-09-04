package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.cmacgm.gbs.rst.api.common.excel.ExcelSheets;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto.SupportItemView;
import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.SupportWorkloadMath;

/**
 * Excel import/export for Exercise Production Support (upsert by Category + Activity + Frequency).
 */
@Component
public class SupportExcelService {

    private static final List<String> HEADERS = List.of(
            "Category", "Activity", "Frequency", "Volume", "UOM", "Minutes", "Comments");
    private static final List<String> REQUIRED = List.of(
            "category", "activity", "frequency", "volume", "uom", "minutes");
    private static final Set<String> UOMS = Set.of(
            "Cases", "Packs", "Queries", "Sessions", "Emails", "Tickets",
            "Calls", "Documents", "Hours", "Other");

    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    /**
     * Blank template used when SharePoint Graph is off.
     *
     * @return xlsx bytes
     */
    public byte[] exportBlank() {
        return ExcelSheets.write("Production Support", HEADERS, List.of());
    }

    /**
     * Exports the current support registry.
     *
     * @param items active support items
     * @return xlsx bytes
     */
    public byte[] export(List<SupportItemView> items) {
        List<List<String>> body = new ArrayList<>();
        for (SupportItemView item : items) {
            body.add(List.of(
                    blank(item.category()),
                    blank(item.activity()),
                    blank(item.frequencyCode()),
                    decimal(item.volume()),
                    blank(item.unitOfMeasure()),
                    decimal(item.workloadPerUnitMinutes()),
                    blank(item.comments())));
        }
        return ExcelSheets.write("Production Support", HEADERS, body);
    }

    /**
     * Parses a support workbook. Duplicate Category + Activity + Frequency keys fail with row numbers.
     *
     * @param inputStream xlsx stream
     * @return parsed rows in file order
     */
    public List<ParsedRow> parse(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw conflict("invalid-excel", "Workbook has no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = readHeaders(sheet.getRow(0));
            List<ParsedRow> out = new ArrayList<>();
            Map<String, Integer> seen = new HashMap<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row excelRow = sheet.getRow(i);
                if (excelRow == null || isBlank(excelRow, headers)) {
                    continue;
                }
                int displayRow = i + 1;
                String category = requireText(excelRow, headers.get("category"), displayRow, "Category");
                String activity = requireText(excelRow, headers.get("activity"), displayRow, "Activity");
                String frequency = requireText(excelRow, headers.get("frequency"), displayRow, "Frequency");
                try {
                    SupportWorkloadMath.requireFrequency(frequency);
                } catch (IllegalArgumentException ex) {
                    throw conflict("invalid-excel", "Row " + displayRow + ": " + ex.getMessage());
                }
                String frequencyCode = frequency.trim().toUpperCase(Locale.ROOT);
                if ("DAY".equals(frequencyCode)) {
                    frequencyCode = "DAILY";
                } else if ("WEEK".equals(frequencyCode)) {
                    frequencyCode = "WEEKLY";
                } else if ("MONTH".equals(frequencyCode)) {
                    frequencyCode = "MONTHLY";
                }
                BigDecimal volume = requireDecimal(excelRow, headers.get("volume"), displayRow, "Volume");
                String uom = canonicalizeUom(requireText(excelRow, headers.get("uom"), displayRow, "UOM"), displayRow);
                BigDecimal minutes = requireDecimal(excelRow, headers.get("minutes"), displayRow, "Minutes");
                String comments = headers.containsKey("comments")
                        ? cell(excelRow, headers.get("comments"))
                        : "";
                if (comments.length() > 500) {
                    throw conflict(
                            "invalid-excel",
                            "Row " + displayRow + ": Comments must be 500 characters or fewer.");
                }
                String key = upsertKey(category, activity, frequencyCode);
                Integer previous = seen.put(key, displayRow);
                if (previous != null) {
                    throw conflict(
                            "invalid-excel",
                            "Rows " + previous + " and " + displayRow
                                    + ": Category + Activity + Frequency must be unique.");
                }
                out.add(new ParsedRow(
                        displayRow, category, activity, frequencyCode, volume, uom, minutes, comments));
            }
            return out;
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw conflict("invalid-excel", "Unable to read support Excel: " + ex.getMessage());
        }
    }

    /**
     * Business key used for file-duplicate checks and upsert matching.
     *
     * @param category category name
     * @param activity activity
     * @param frequencyCode DAILY / WEEKLY / MONTHLY
     * @return normalized key
     */
    public static String upsertKey(String category, String activity, String frequencyCode) {
        return normalize(category) + "\n" + normalize(activity) + "\n" + normalize(frequencyCode);
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
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "");
        return switch (value) {
            case "standardcategory", "categoryname" -> "category";
            case "freq" -> "frequency";
            case "unitofmeasure", "unit" -> "uom";
            case "mins", "minsunit", "mins/unit", "workloadperunitminutes", "minutesunit" -> "minutes";
            case "comment", "remarks" -> "comments";
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

    private String requireText(Row row, Integer index, int displayRow, String label) {
        String value = cell(row, index);
        if (value.isBlank()) {
            throw conflict("invalid-excel", "Row " + displayRow + ": " + label + " is required.");
        }
        return value;
    }

    private BigDecimal requireDecimal(Row row, Integer index, int displayRow, String label) {
        String raw = cell(row, index);
        if (raw.isBlank()) {
            throw conflict("invalid-excel", "Row " + displayRow + ": " + label + " is required.");
        }
        try {
            BigDecimal value = new BigDecimal(raw.replace(",", ""));
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                throw conflict(
                        "invalid-excel",
                        "Row " + displayRow + ": " + label + " must be zero or greater.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw conflict("invalid-excel", "Row " + displayRow + ": " + label + " must be a number.");
        }
    }

    private String canonicalizeUom(String raw, int displayRow) {
        for (String allowed : UOMS) {
            if (allowed.equalsIgnoreCase(raw.trim())) {
                return allowed;
            }
        }
        throw conflict(
                "invalid-excel",
                "Row " + displayRow + ": UOM must be one of " + String.join(", ", UOMS) + ".");
    }

    private String cell(Row row, Integer index) {
        if (index == null) {
            return "";
        }
        return formatter.formatCellValue(row.getCell(index)).trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }

    /**
     * One parsed support row from Excel.
     */
    public record ParsedRow(
            int displayRow,
            String categoryName,
            String activity,
            String frequencyCode,
            BigDecimal volume,
            String unitOfMeasure,
            BigDecimal workloadPerUnitMinutes,
            String comments) {
    }
}
