package com.cmacgm.gbs.rst.api.associateddata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Production Support annualization and FTE formulas (BRD).
 * Computed at read / simulation time from Support inputs + Team Setup + Calendar.
 */
public final class SupportWorkloadMath {

    private static final BigDecimal WEEKLY = BigDecimal.valueOf(52);
    private static final BigDecimal MONTHLY = BigDecimal.valueOf(12);
    private static final BigDecimal DEFAULT_WORKING_DAYS = BigDecimal.valueOf(261);
    private static final BigDecimal FALLBACK_ANNUAL_HOURS = BigDecimal.valueOf(2080);
    private static final BigDecimal SIXTY = new BigDecimal("60");

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
    public static BigDecimal fteAnnualHours(ExerciseTeamSetup setup, BigDecimal workingDaysPerYear) {
        if (setup == null) {
            return FALLBACK_ANNUAL_HOURS;
        }
        BigDecimal hours = setup.workingHoursPerDay();
        BigDecimal availability = setup.getAvailabilityRatio();
        BigDecimal capacity = setup.capacityRatio(workingDaysPerYear);
        if (hours == null || availability == null || workingDaysPerYear == null || capacity == null) {
            return FALLBACK_ANNUAL_HOURS;
        }
        if (hours.compareTo(BigDecimal.ZERO) <= 0
                || availability.compareTo(BigDecimal.ZERO) <= 0
                || workingDaysPerYear.compareTo(BigDecimal.ZERO) <= 0
                || capacity.compareTo(BigDecimal.ZERO) <= 0) {
            return FALLBACK_ANNUAL_HOURS;
        }
        return hours.multiply(availability).multiply(workingDaysPerYear).multiply(capacity);
    }

    /**
     * Derives annual hours and FTE for one support activity.
     */
    public static Derived derive(
            ExerciseProductionSupportItem item,
            BigDecimal workingDaysPerYear,
            BigDecimal fteAnnualHours) {
        BigDecimal multiplier = annualMultiplier(item.getFrequencyCode(), workingDaysPerYear);
        BigDecimal hours = item.getVolume()
                .multiply(item.getWorkloadPerUnitMinutes())
                .multiply(multiplier)
                .divide(SIXTY, 6, RoundingMode.HALF_UP);
        BigDecimal fte = hours.divide(fteAnnualHours, 6, RoundingMode.HALF_UP);
        return new Derived(multiplier, hours, fte);
    }

    public record Derived(
            BigDecimal annualMultiplier, BigDecimal workloadPerYearHours, BigDecimal supportFte) {
    }
}
