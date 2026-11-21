package com.cmacgm.gbs.rst.api.sourcedata.application;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Text and number fitting shared by the three imports.
 */
final class SourceValues {

    private SourceValues() {
    }

    static String fit(String value, int max) {
        if (value == null) {
            return null;
        }
        String text = value.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    static String upper(String value, int max) {
        String fitted = fit(value, max);
        return fitted == null ? null : fitted.toUpperCase(Locale.ROOT);
    }

    static String empty(String value) {
        return value == null ? "" : value;
    }

    /**
     * @param value raw number
     * @return parsed value, or null when it is not a number
     */
    static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace(",", "");
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
