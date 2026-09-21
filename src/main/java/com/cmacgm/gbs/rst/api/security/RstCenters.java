package com.cmacgm.gbs.rst.api.security;

import java.util.Locale;
import java.util.Set;

/**
 * Canonical GBS Center names used by SSO, Timesheet snapshots, and product scope.
 */
public final class RstCenters {

    public static final String GBS_CHINA = "GBS CHINA";
    public static final String GBS_INDIA = "GBS INDIA";
    public static final String GBS_LEBANON = "GBS LEBANON";
    public static final String GBS_ESTONIA = "GBS ESTONIA";
    public static final String GBS_COSTA_RICA = "GBS COSTA RICA";
    public static final String GBS_PHILIPPINES = "GBS PHILIPPINES";
    public static final String GBS_PORTUGAL = "GBS PORTUGAL";

    public static final Set<String> ALL = Set.of(
            GBS_CHINA,
            GBS_INDIA,
            GBS_LEBANON,
            GBS_ESTONIA,
            GBS_COSTA_RICA,
            GBS_PHILIPPINES,
            GBS_PORTUGAL);

    private RstCenters() {
    }

    /**
     * @param value raw center
     * @return canonical name when it matches the allow-list (case-insensitive)
     */
    public static String canonicalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (String center : ALL) {
            if (center.equalsIgnoreCase(trimmed)) {
                return center;
            }
        }
        return null;
    }

    /**
     * @param value raw center
     * @return true when the value is one of the canonical GBS centers
     */
    public static boolean isKnown(String value) {
        return canonicalize(value) != null;
    }

    /**
     * Finds the Center when free text contains exactly one configured name.
     * Extra tokens such as a SharePoint timestamp or a download {@code (2)}
     * suffix are ignored. Zero or two-plus distinct Centers yield {@code null}.
     *
     * @param text file name or other free text
     * @return canonical Center, or null
     */
    public static String uniqueIn(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String haystack = text.toUpperCase(Locale.ROOT);
        String found = null;
        for (String center : ALL) {
            if (!haystack.contains(center.toUpperCase(Locale.ROOT))) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = center;
        }
        return found;
    }

    /**
     * Normalizes a known center; unknown values are trimmed as-is.
     *
     * @param value raw center
     * @return canonical or trimmed value
     */
    public static String normalizeOrTrim(String value) {
        String known = canonicalize(value);
        if (known != null) {
            return known;
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
