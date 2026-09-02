package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
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
            requireHeader(parser, HEADER_ID);
            requireHeader(parser, HEADER_APPLICABILITY);
            Set<String> codes = new LinkedHashSet<>();
            for (CSVRecord record : parser) {
                if (!isYes(record.get(HEADER_APPLICABILITY))) {
                    continue;
                }
                String id = normalize(record.get(HEADER_ID));
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

    private static void requireHeader(CSVParser parser, String name) {
        if (!parser.getHeaderNames().contains(name)) {
            throw conflict("Missing GBS Process header: " + name);
        }
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

    private static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, TimesheetSyncErrorCode.INVALID_HEADER.code(), detail);
    }
}
