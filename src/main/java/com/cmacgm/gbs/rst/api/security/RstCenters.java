package com.cmacgm.gbs.rst.api.security;

import java.util.Set;

/**
 * Canonical GBS Center names used by SSO, Timesheet snapshots, and product scope.
 */
public final class RstCenters {

    public static final String GBS_CHINA_INDIA = "GBS CHINA INDIA";
    public static final String GBS_CHINA_LEBANON = "GBS CHINA LEBANON";
    public static final String GBS_CHINA_ESTONIA = "GBS CHINA ESTONIA";
    public static final String GBS_CHINA_COSTA_RICA = "GBS CHINA COSTA RICA";
    public static final String GBS_CHINA_PHILIPPINES = "GBS CHINA PHILIPPINES";
    public static final String GBS_CHINA_PORTUGAL = "GBS CHINA PORTUGAL";

    public static final Set<String> ALL = Set.of(
            GBS_CHINA_INDIA,
            GBS_CHINA_LEBANON,
            GBS_CHINA_ESTONIA,
            GBS_CHINA_COSTA_RICA,
            GBS_CHINA_PHILIPPINES,
            GBS_CHINA_PORTUGAL);

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
     * @return true when the value is one of the six canonical GBS centers
     */
    public static boolean isKnown(String value) {
        return canonicalize(value) != null;
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
