package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * RST-applicable PL3 codes from GBS Process ({@code ID} where
 * {@code RST Applicability} is Yes).
 */
public final class GbsProcessCatalog {

    private static final String HEADER_ID = "ID";
    private static final String HEADER_APPLICABILITY = "RST Applicability";

    private final Set<String> rstYesPl3Codes;

    private GbsProcessCatalog(Set<String> rstYesPl3Codes) {
        this.rstYesPl3Codes = Collections.unmodifiableSet(new LinkedHashSet<>(rstYesPl3Codes));
    }

    /**
     * Test helper that treats every listed PL3 as RST-applicable.
     *
     * @param pl3Codes Timesheet pl3_code values
     * @return catalog
     */
    public static GbsProcessCatalog allowing(String... pl3Codes) {
        Set<String> codes = new LinkedHashSet<>();
        if (pl3Codes != null) {
            for (String pl3Code : pl3Codes) {
                String normalized = normalize(pl3Code);
                if (normalized != null) {
                    codes.add(normalized);
                }
            }
        }
        return new GbsProcessCatalog(codes);
    }

    /**
     * Parses the GBS Process CSV. Only {@code ID} and {@code RST Applicability}
     * are used.
     *
     * @param inputStream CSV stream
     * @return catalog
     */
    public static GbsProcessCatalog parse(InputStream inputStream) {
        if (inputStream == null) {
            throw conflict("GBS Process catalog is missing.");
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setIgnoreSurroundingSpaces(true)
                .build();
        try (CSVParser parser = format.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String idHeader = requireHeader(parser, HEADER_ID);
            String applicabilityHeader = requireHeader(parser, HEADER_APPLICABILITY);
            Set<String> codes = new LinkedHashSet<>();
            for (CSVRecord record : parser) {
                if (!isYes(record.get(applicabilityHeader))) {
                    continue;
                }
                String id = normalize(record.get(idHeader));
                if (id != null) {
                    codes.add(id);
                }
            }
            return new GbsProcessCatalog(codes);
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw conflict("Unable to read GBS Process catalog: " + ex.getMessage());
        }
    }

    /**
     * Builds a catalog from SharePoint list item field bags. Looks up
     * {@code ID} / {@code id} and {@code RST Applicability}
     * (SharePoint internal name {@code RSTApplicability}, also accepting
     * {@code RST_x0020_Applicability}).
     *
     * @param rows field maps from Graph list items
     * @return catalog
     */
    public static GbsProcessCatalog fromSharePointFields(List<Map<String, Object>> rows) {
        if (rows == null) {
            throw conflict("GBS Process catalog is missing.");
        }
        Set<String> codes = new LinkedHashSet<>();
        boolean sawApplicability = false;
        boolean sawId = false;
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Object applicability = fieldValue(row, HEADER_APPLICABILITY);
            Object idValue = fieldValue(row, HEADER_ID);
            if (applicability != null) {
                sawApplicability = true;
            }
            if (idValue != null) {
                sawId = true;
            }
            if (!isYes(stringValue(applicability))) {
                continue;
            }
            String id = normalize(stringValue(idValue));
            if (id != null) {
                codes.add(id);
            }
        }
        if (!rows.isEmpty() && !sawApplicability) {
            throw conflict("Missing GBS Process field: " + HEADER_APPLICABILITY);
        }
        if (!rows.isEmpty() && !sawId) {
            throw conflict("Missing GBS Process field: " + HEADER_ID);
        }
        return new GbsProcessCatalog(codes);
    }

    /**
     * @param pl3Code Timesheet pl3_code
     * @return true when the process is RST-applicable
     */
    public boolean applies(String pl3Code) {
        String normalized = normalize(pl3Code);
        return normalized != null && rstYesPl3Codes.contains(normalized);
    }

    /**
     * @return RST-applicable PL3 codes
     */
    public Set<String> rstYesPl3Codes() {
        return rstYesPl3Codes;
    }

    /** @return actual header name in the file (may include a UTF-8 BOM prefix) */
    private static String requireHeader(CSVParser parser, String name) {
        for (String header : parser.getHeaderNames()) {
            if (header == null) {
                continue;
            }
            String normalized = header.charAt(0) == '\uFEFF' ? header.substring(1) : header;
            if (name.equals(normalized) || name.equals(header)) {
                return header;
            }
        }
        throw conflict("Missing GBS Process header: " + name);
    }

    private static boolean isYes(String value) {
        return "yes".equalsIgnoreCase(value == null ? "" : value.trim());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.matches("\\d+\\.0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }

    private static Object fieldValue(Map<String, Object> row, String displayName) {
        Object direct = row.get(displayName);
        if (direct != null) {
            return direct;
        }
        // SharePoint often drops spaces: "RST Applicability" → RSTApplicability
        String stripped = displayName.replace(" ", "");
        Object strippedValue = row.get(stripped);
        if (strippedValue != null) {
            return strippedValue;
        }
        String encoded = displayName.replace(" ", "_x0020_");
        Object sharePoint = row.get(encoded);
        if (sharePoint != null) {
            return sharePoint;
        }
        if (HEADER_ID.equals(displayName)) {
            Object lower = row.get("id");
            if (lower != null) {
                return lower;
            }
        }
        String needle = displayName.toLowerCase(Locale.ROOT);
        String strippedNeedle = stripped.toLowerCase(Locale.ROOT);
        String encodedNeedle = encoded.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (key.equals(needle) || key.equals(strippedNeedle) || key.equals(encodedNeedle)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        return String.valueOf(value);
    }

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, TimesheetSyncErrorCode.INVALID_HEADER.code(), detail);
    }
}
