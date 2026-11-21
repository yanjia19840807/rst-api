package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private static final String HEADER_LEVEL_3 = "Level 3";
    private static final String HEADER_LEVEL_1 = "Level 1";
    private static final String HEADER_LEVEL_2 = "Level 2";
    private static final String HEADER_DOMAIN = "Domain";

    private final Set<String> rstYesPl3Codes;
    private final Map<String, NameHit> names;
    private final Map<String, String> domains;

    private GbsProcessCatalog(Set<String> rstYesPl3Codes) {
        this(rstYesPl3Codes, Map.of(), Map.of());
    }

    private GbsProcessCatalog(
            Set<String> rstYesPl3Codes, Map<String, NameHit> names, Map<String, String> domains) {
        this.rstYesPl3Codes = Collections.unmodifiableSet(new LinkedHashSet<>(rstYesPl3Codes));
        this.names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
        this.domains = Collections.unmodifiableMap(new LinkedHashMap<>(domains));
    }

    /**
     * One RST-applicable Level 3. Blank PL1, PL2, or domain are empty strings.
     *
     * @param pl3Code catalog id
     * @param name Level 3 name
     * @param domain catalog domain spelling
     * @param pl1 process level 1
     * @param pl2 process level 2
     */
    public record Level3(String pl3Code, String name, String domain, String pl1, String pl2) {
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
            String level3Header = optionalHeader(parser, HEADER_LEVEL_3);
            String level1Header = optionalHeader(parser, HEADER_LEVEL_1);
            String level2Header = optionalHeader(parser, HEADER_LEVEL_2);
            String domainHeader = optionalHeader(parser, HEADER_DOMAIN);
            Set<String> codes = new LinkedHashSet<>();
            Map<String, NameHit> names = new LinkedHashMap<>();
            Map<String, String> domains = new LinkedHashMap<>();
            for (CSVRecord record : parser) {
                rememberDomain(domains, text(record, domainHeader));
                if (!isYes(record.get(applicabilityHeader))) {
                    continue;
                }
                String id = normalize(record.get(idHeader));
                if (id != null) {
                    codes.add(id);
                }
                rememberLevel3(
                        names,
                        id,
                        text(record, level3Header),
                        text(record, domainHeader),
                        text(record, level1Header),
                        text(record, level2Header));
            }
            return new GbsProcessCatalog(codes, names, domains);
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
        Map<String, NameHit> names = new LinkedHashMap<>();
        Map<String, String> domains = new LinkedHashMap<>();
        boolean sawApplicability = false;
        boolean sawId = false;
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Object applicability = fieldValue(row, HEADER_APPLICABILITY);
            Object idValue = fieldValue(row, HEADER_ID);
            String domain = clean(stringValue(fieldValue(row, HEADER_DOMAIN)));
            rememberDomain(domains, domain);
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
            rememberLevel3(
                    names,
                    id,
                    clean(stringValue(fieldValue(row, HEADER_LEVEL_3))),
                    domain,
                    clean(stringValue(fieldValue(row, HEADER_LEVEL_1))),
                    clean(stringValue(fieldValue(row, HEADER_LEVEL_2))));
        }
        if (!rows.isEmpty() && !sawApplicability) {
            throw conflict("Missing GBS Process field: " + HEADER_APPLICABILITY);
        }
        if (!rows.isEmpty() && !sawId) {
            throw conflict("Missing GBS Process field: " + HEADER_ID);
        }
        return new GbsProcessCatalog(codes, names, domains);
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

    /**
     * Resolves one RST-applicable Level 3 by name. Matching ignores case and
     * repeated spaces. Two Yes rows with the same name and different ids are
     * ambiguous and return empty.
     *
     * @param name SKPI process name
     * @return the single match
     */
    public Optional<Level3> level3ByName(String name) {
        NameHit hit = names.get(nameKey(name));
        if (hit == null || hit.ambiguous || hit.value == null) {
            return Optional.empty();
        }
        return Optional.of(hit.value);
    }

    /**
     * @param name SKPI process name
     * @return true when more than one RST-applicable id shares the name
     */
    public boolean level3Ambiguous(String name) {
        NameHit hit = names.get(nameKey(name));
        return hit != null && hit.ambiguous;
    }

    /**
     * Catalog spelling of a domain. Blank and unknown domains return null.
     *
     * @param domain SKPI or catalog domain
     * @return catalog spelling
     */
    public String canonicalDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        return domains.get(nameKey(domain));
    }

    private static void rememberDomain(Map<String, String> domains, String domain) {
        if (domain == null) {
            return;
        }
        domains.putIfAbsent(nameKey(domain), domain);
    }

    private static void rememberLevel3(
            Map<String, NameHit> names,
            String id,
            String level3,
            String domain,
            String pl1,
            String pl2) {
        if (id == null || level3 == null) {
            return;
        }
        String key = nameKey(level3);
        Level3 entry = new Level3(id, level3, domain == null ? "" : domain, pl1 == null ? "" : pl1, pl2 == null ? "" : pl2);
        NameHit existing = names.get(key);
        if (existing == null) {
            names.put(key, NameHit.one(entry));
            return;
        }
        if (!existing.ambiguous && existing.value != null && !existing.value.pl3Code().equals(id)) {
            names.put(key, NameHit.ambiguous());
        }
    }

    private static String text(CSVRecord record, String header) {
        if (header == null) {
            return null;
        }
        return clean(record.get(header));
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim().replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text;
    }

    private static String nameKey(String value) {
        String cleaned = clean(value);
        return cleaned == null ? "" : cleaned.toLowerCase(Locale.ROOT);
    }

    private static String optionalHeader(CSVParser parser, String name) {
        for (String header : parser.getHeaderNames()) {
            if (header == null) {
                continue;
            }
            String normalized = header.charAt(0) == '\uFEFF' ? header.substring(1) : header;
            if (name.equals(normalized) || name.equals(header)) {
                return header;
            }
        }
        return null;
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

    private static final class NameHit {
        private final Level3 value;
        private final boolean ambiguous;

        private NameHit(Level3 value, boolean ambiguous) {
            this.value = value;
            this.ambiguous = ambiguous;
        }

        private static NameHit one(Level3 value) {
            return new NameHit(value, false);
        }

        private static NameHit ambiguous() {
            return new NameHit(null, true);
        }
    }
}
