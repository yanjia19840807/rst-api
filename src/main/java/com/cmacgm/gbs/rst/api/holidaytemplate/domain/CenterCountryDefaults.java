package com.cmacgm.gbs.rst.api.holidaytemplate.domain;

import java.util.Locale;
import java.util.Map;

/**
 * Temporary Center → weekend defaults until a master-data table exists.
 */
public final class CenterCountryDefaults {

    private static final Map<String, String> WEEKEND_BY_CENTER = Map.ofEntries(
            Map.entry("GBS China", "SAT_SUN"),
            Map.entry("GBS India", "SAT_SUN"),
            Map.entry("GBS Philippines", "SAT_SUN"),
            Map.entry("GBS Costa Rica", "SAT_SUN"),
            Map.entry("GBS Lebanon", "SAT_SUN"),
            Map.entry("GBS Estonia", "SAT_SUN"),
            Map.entry("GBS Portugal", "SAT_SUN"));

    private CenterCountryDefaults() {
    }

    /**
     * Resolves default weekend code for a GBS Center name.
     */
    public static Defaults resolve(String center) {
        if (center == null || center.isBlank()) {
            return new Defaults("SAT_SUN");
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
        return new Defaults("SAT_SUN");
    }

    /** Weekend default for a Center. */
    public record Defaults(String weekendCode) {
    }
}
