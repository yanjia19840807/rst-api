package com.cmacgm.gbs.rst.api.holidaytemplate.domain;

import java.util.Locale;
import java.util.Map;

/**
 * Temporary Center → weekend defaults until a master-data table exists.
 */
public final class CenterCountryDefaults {

    private static final Map<String, String> WEEKEND_BY_CENTER = Map.ofEntries(
            Map.entry("GBS China", WeekendCode.DEFAULT_STORED),
            Map.entry("GBS India", WeekendCode.DEFAULT_STORED),
            Map.entry("GBS Philippines", WeekendCode.DEFAULT_STORED),
            Map.entry("GBS Costa Rica", WeekendCode.DEFAULT_STORED),
            Map.entry("GBS Lebanon", WeekendCode.DEFAULT_STORED),
            Map.entry("GBS Estonia", WeekendCode.DEFAULT_STORED),
            Map.entry("GBS Portugal", WeekendCode.DEFAULT_STORED));

    private CenterCountryDefaults() {
    }

    /**
     * Resolves default weekend code for a GBS Center name.
     */
    public static Defaults resolve(String center) {
        if (center == null || center.isBlank()) {
            return new Defaults(WeekendCode.DEFAULT_STORED);
        }
        String known = WEEKEND_BY_CENTER.get(center.trim());
        if (known != null) {
            return new Defaults(known);
        }
        String normalized = center.trim().toUpperCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : WEEKEND_BY_CENTER.entrySet()) {
            if (entry.getKey().toUpperCase(Locale.ROOT).equals(normalized)) {
                return new Defaults(entry.getValue());
            }
        }
        return new Defaults(WeekendCode.DEFAULT_STORED);
    }

    /** Weekend default for a Center. */
    public record Defaults(String weekendCode) {
    }
}
