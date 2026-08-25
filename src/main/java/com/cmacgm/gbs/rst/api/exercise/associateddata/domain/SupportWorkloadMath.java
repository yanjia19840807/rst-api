package com.cmacgm.gbs.rst.api.exercise.associateddata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Production Support annualization and FTE formulas (BRD).
 * Computed at read / simulation time from Support inputs + Team Setup + Calendar.
 */
public final class SupportWorkloadMath {

    private static final BigDecimal WEEKLY = BigDecimal.valueOf(52);
    private static final BigDecimal MONTHLY = BigDecimal.valueOf(12);
    private static final BigDecimal SIXTY = new BigDecimal("60");

    private SupportWorkloadMath() {
    }

    /**
     * Maps frequency to annual multiplier.
     * Daily → WorkingDays/year; Weekly → 52; Monthly → 12.
     *
     * @return multiplier, or null when Daily has no working-days input
     */
    public static BigDecimal annualMultiplier(String frequencyCode, BigDecimal workingDaysPerYear) {
        String code = frequencyCode == null ? "" : frequencyCode.trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "DAILY", "DAY" -> workingDaysPerYear != null && workingDaysPerYear.compareTo(BigDecimal.ZERO) > 0
                    ? workingDaysPerYear
                    : null;
            case "WEEKLY", "WEEK" -> WEEKLY;
            case "MONTHLY", "MONTH" -> MONTHLY;
            default -> throw new IllegalArgumentException(
                    "frequencyCode must be DAILY, WEEKLY, or MONTHLY.");
        };
    }

    /**
     * Validates a frequency code without requiring working days.
     */
    public static void requireFrequency(String frequencyCode) {
        annualMultiplier(frequencyCode, BigDecimal.ONE);
    }

    /**
     * BRD FTE denominator: WorkingHrPerDay × Availability × WorkingDays × CapacityRatio.
     *
     * @return annual hours, or null when Team Setup inputs are incomplete
     */
    public static BigDecimal fteAnnualHours(ExerciseTeamSetup setup, BigDecimal workingDaysPerYear) {
        if (setup == null) {
            return null;
        }
        BigDecimal hours = setup.workingHoursPerDay();
        BigDecimal availability = setup.getAvailabilityRatio();
        BigDecimal capacity = setup.capacityRatio(workingDaysPerYear);
        if (hours == null || availability == null || workingDaysPerYear == null || capacity == null) {
            return null;
        }
        if (hours.compareTo(BigDecimal.ZERO) <= 0
                || availability.compareTo(BigDecimal.ZERO) <= 0
                || workingDaysPerYear.compareTo(BigDecimal.ZERO) <= 0
                || capacity.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return hours.multiply(availability).multiply(workingDaysPerYear).multiply(capacity);
    }

    /**
     * Whether Team Setup + calendar can compute the FTE denominator.
     */
    public static boolean teamSetupComplete(ExerciseTeamSetup setup, BigDecimal workingDaysPerYear) {
        return fteAnnualHours(setup, workingDaysPerYear) != null;
    }

    /**
     * Derives annual hours and FTE for one support activity.
     * Hours / FTE stay null when the multiplier or denominator is missing.
     */
    public static Derived derive(
            ExerciseProductionSupportItem item,
            BigDecimal workingDaysPerYear,
            BigDecimal fteAnnualHours) {
        BigDecimal multiplier = annualMultiplier(item.getFrequencyCode(), workingDaysPerYear);
        if (multiplier == null) {
            return new Derived(null, null, null);
        }
        BigDecimal hours = item.getVolume()
                .multiply(item.getWorkloadPerUnitMinutes())
                .multiply(multiplier)
                .divide(SIXTY, 6, RoundingMode.HALF_UP);
        BigDecimal fte = fteAnnualHours == null || fteAnnualHours.compareTo(BigDecimal.ZERO) <= 0
                ? null
                : hours.divide(fteAnnualHours, 6, RoundingMode.HALF_UP);
        return new Derived(multiplier, hours, fte);
    }

    /**
     * Sums Support FTE. Empty list is 0; incomplete Team Setup with items is null.
     */
    public static BigDecimal totalSupportFte(
            List<ExerciseProductionSupportItem> items,
            ExerciseTeamSetup setup,
            BigDecimal workingDaysPerYear) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal fteHours = fteAnnualHours(setup, workingDaysPerYear);
        if (fteHours == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ExerciseProductionSupportItem item : items) {
            try {
                BigDecimal fte = derive(item, workingDaysPerYear, fteHours).supportFte();
                if (fte == null) {
                    return null;
                }
                total = total.add(fte);
            } catch (IllegalArgumentException ignored) {
                // skip historical rows whose frequency codes are no longer recognized
            }
        }
        return total;
    }

    public record Derived(
            BigDecimal annualMultiplier, BigDecimal workloadPerYearHours, BigDecimal supportFte) {
    }
}
