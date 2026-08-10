package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * BRD Production Support annualization and FTE denominator helpers.
 */
public final class SupportWorkloadMath {

    private static final BigDecimal WEEKLY = BigDecimal.valueOf(52);
    private static final BigDecimal MONTHLY = BigDecimal.valueOf(12);
    private static final BigDecimal DEFAULT_WORKING_DAYS = BigDecimal.valueOf(261);
    private static final BigDecimal FALLBACK_ANNUAL_HOURS = BigDecimal.valueOf(2080);

    private SupportWorkloadMath() {
    }

    /**
     * Maps frequency to annual multiplier.
     * Daily → WorkingDays/year; Weekly → 52; Monthly → 12.
     */
    public static BigDecimal annualMultiplier(String frequencyCode, BigDecimal workingDaysPerYear) {
        String code = frequencyCode == null ? "" : frequencyCode.trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "DAILY", "DAY" -> workingDaysPerYear != null && workingDaysPerYear.compareTo(BigDecimal.ZERO) > 0
                    ? workingDaysPerYear
                    : DEFAULT_WORKING_DAYS;
            case "WEEKLY", "WEEK" -> WEEKLY;
            case "MONTHLY", "MONTH" -> MONTHLY;
            default -> throw new IllegalArgumentException(
                    "frequencyCode must be DAILY, WEEKLY, or MONTHLY.");
        };
    }

    /**
     * BRD FTE denominator: WorkingHrPerDay × Availability × WorkingDays × CapacityRatio.
     * Falls back to 2080 when Team Setup inputs are incomplete.
     */
    public static BigDecimal fteAnnualHours(ExerciseTeamSetup setup) {
        if (setup == null) {
            return FALLBACK_ANNUAL_HOURS;
        }
        BigDecimal hours = setup.getWorkingHoursPerDay();
        BigDecimal availability = setup.getAvailabilityRatio();
        BigDecimal workingDays = setup.getWorkingDaysPerYear();
        BigDecimal capacity = setup.getCapacityRatio();
        if (hours == null || availability == null || workingDays == null || capacity == null) {
            return FALLBACK_ANNUAL_HOURS;
        }
        if (hours.compareTo(BigDecimal.ZERO) <= 0
                || availability.compareTo(BigDecimal.ZERO) <= 0
                || workingDays.compareTo(BigDecimal.ZERO) <= 0
                || capacity.compareTo(BigDecimal.ZERO) <= 0) {
            return FALLBACK_ANNUAL_HOURS;
        }
        return hours.multiply(availability).multiply(workingDays).multiply(capacity);
    }
}
