package com.cmacgm.gbs.rst.api.timesheet.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical customer country for Daily and Monthly rows.
 *
 * <p>UAT writes one name, for example {@code MALAYSIA}. Production slices the
 * same country with a trailing integer, for example {@code MALAYSIA 1} and
 * {@code BOLLORE CHINA 6}. The slice shares site, carrier, and PL3 with its
 * siblings, so the integer is not a different country. Folding it here lets
 * Monthly Delivery HC sum those slices on the existing country key.
 */
public final class CustomerCountry {

    private static final Pattern NUMBERED_SLICE = Pattern.compile("^(.*\\S)\\s+\\d+$");

    private CustomerCountry() {
    }

    /**
     * Returns the country name used as the KPI key.
     *
     * @param value trimmed cell text, or null
     * @return base name, or null when {@code value} is null
     */
    public static String canonical(String value) {
        if (value == null) {
            return null;
        }
        Matcher slice = NUMBERED_SLICE.matcher(value);
        return slice.matches() ? slice.group(1) : value;
    }
}
