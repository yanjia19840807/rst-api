package com.cmacgm.gbs.rst.api.exercise.scenario.application.sizing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Workbook §11.2 sizing formulas (monthly + daily).
 */
public final class SizingMath {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal SECONDS_PER_HOUR = new BigDecimal("3600");
    private static final BigDecimal SIXTY = new BigDecimal("60");

    private SizingMath() {
    }

    /**
     * Monthly Excel P: Forecast × (1 − Auto) × (1 + Commercial).
     */
    public static BigDecimal monthlyManualVolume(
            BigDecimal forecastVolume, BigDecimal automationRatio, BigDecimal commercialRatio) {
        return applyManualMultipliers(forecastVolume, automationRatio, commercialRatio, BigDecimal.ZERO);
    }

    /**
     * Daily Excel T: Forecast × (1 − Auto) × (1 + Commercial) × (1 + Daily Adj).
     */
    public static BigDecimal dailyManualVolume(
            BigDecimal forecastVolume,
            BigDecimal automationRatio,
            BigDecimal commercialRatio,
            BigDecimal dailyAdjustmentRatio) {
        return applyManualMultipliers(
                forecastVolume, automationRatio, commercialRatio, dailyAdjustmentRatio);
    }

    /**
     * Nominal HC without overtime (fractional HC, 6 decimal places).
     */
    public static BigDecimal nominalHcWithoutOt(
            BigDecimal manualVolume,
            BigDecimal cycleTimeSeconds,
            BigDecimal workDays,
            BigDecimal workingHoursPerDay,
            BigDecimal availabilityRatio,
            BigDecimal capacityRatio) {
        BigDecimal denominator = workDays
                .multiply(workingHoursPerDay, MC)
                .multiply(nz(availabilityRatio), MC)
                .multiply(nz(capacityRatio), MC);
        if (denominator.signum() <= 0) {
            throw new IllegalArgumentException("WorkDays × WorkingHr × Avail × Cap must be positive.");
        }
        BigDecimal raw = manualVolume
                .multiply(cycleTimeSeconds, MC)
                .divide(SECONDS_PER_HOUR, MC)
                .divide(denominator, MC);
        return raw.setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Nominal HC with overtime (fractional HC, 6 decimal places).
     */
    public static BigDecimal nominalHcWithOt(
            BigDecimal manualVolume,
            BigDecimal cycleTimeSeconds,
            BigDecimal workDays,
            BigDecimal weekendDays,
            BigDecimal workingHoursPerDay,
            BigDecimal maxOvertimeMinutes,
            BigDecimal availabilityRatio,
            BigDecimal capacityRatio,
            BigDecimal weekendShiftHc) {
        BigDecimal hoursWithOt = workingHoursPerDay.add(
                nz(maxOvertimeMinutes).divide(SIXTY, MC), MC);
        BigDecimal denominator = workDays
                .multiply(hoursWithOt, MC)
                .multiply(nz(availabilityRatio), MC)
                .multiply(nz(capacityRatio), MC);
        if (denominator.signum() <= 0 || workDays.signum() <= 0) {
            throw new IllegalArgumentException("WorkDays and OT capacity denominator must be positive.");
        }
        BigDecimal raw = manualVolume
                .multiply(cycleTimeSeconds, MC)
                .divide(SECONDS_PER_HOUR, MC)
                .divide(denominator, MC)
                .subtract(weekendDays.divide(workDays, MC).multiply(nz(weekendShiftHc), MC), MC);
        if (raw.signum() < 0) {
            raw = BigDecimal.ZERO;
        }
        return raw.setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Excel Input TotalAgent. Tenure buckets win; otherwise Shared KPI Delivery HC.
     */
    public static BigDecimal actualHeadcount(BigDecimal totalAgents, BigDecimal deliveryHc) {
        if (totalAgents != null && totalAgents.signum() > 0) {
            return totalAgents;
        }
        return nz(deliveryHc);
    }

    /** Inclusive daily-chart history: sizingMonth − 2 … sizingMonth. */
    public static LocalDate dailyHistoryStart(YearMonth sizingMonth) {
        return sizingMonth.minusMonths(2).atDay(1);
    }

    /** Inclusive daily-chart history end (last day of sizing month). */
    public static LocalDate dailyHistoryEnd(YearMonth sizingMonth) {
        return sizingMonth.atEndOfMonth();
    }

    /**
     * Excel Input "Full Period" daily chart start: first actual day, else 1 Jan of sizing year.
     */
    public static LocalDate dailyFullPeriodStart(YearMonth sizingMonth, LocalDate firstActual) {
        if (firstActual != null) {
            return firstActual;
        }
        return YearMonth.of(sizingMonth.getYear(), 1).atDay(1);
    }

    /** Excel Input "Full Period" daily chart end: last day of the daily forecast month. */
    public static LocalDate dailyFullPeriodEnd(YearMonth sizingMonth) {
        return sizingMonth.plusMonths(1).atEndOfMonth();
    }

    /** Chart history months: sizingMonth − 2 … sizingMonth. */
    public static List<YearMonth> monthlyHistoryMonths(YearMonth sizingMonth) {
        return List.of(sizingMonth.minusMonths(2), sizingMonth.minusMonths(1), sizingMonth);
    }

    /**
     * Right Sizing HC that represents a completed sizing. Null or non-positive means no result.
     */
    public static BigDecimal measuredRightSizingHc(BigDecimal rightSizingHc) {
        if (rightSizingHc == null || rightSizingHc.signum() <= 0) {
            return null;
        }
        return rightSizingHc;
    }

    /**
     * Capacity creation: Actual HC (TotalAgent) − RS HC − Support.
     * Null Support FTE means it could not be computed — do not treat it as zero.
     */
    public static BigDecimal capacityCreation(
            BigDecimal actualHc, BigDecimal rightSizingHc, BigDecimal productionSupportFte) {
        if (productionSupportFte == null) {
            return null;
        }
        return nz(actualHc)
                .subtract(nz(rightSizingHc), MC)
                .subtract(productionSupportFte, MC)
                .setScale(6, RoundingMode.HALF_UP);
    }

    /** Daily simulation HC by day type. */
    public static BigDecimal simulationHc(
            boolean holiday,
            boolean workingDay,
            BigDecimal simulationAgent,
            BigDecimal skeletonRatio,
            BigDecimal weekendShiftHc) {
        if (holiday) {
            return nz(simulationAgent)
                    .multiply(nz(skeletonRatio), MC)
                    .setScale(0, RoundingMode.FLOOR);
        }
        if (workingDay) {
            return nz(simulationAgent).setScale(6, RoundingMode.HALF_UP);
        }
        return nz(weekendShiftHc).setScale(6, RoundingMode.HALF_UP);
    }

    /** Daily standard production capacity (Excel ROUNDDOWN). */
    public static BigDecimal standardCapacity(
            BigDecimal simulationHc,
            BigDecimal workingHoursPerDay,
            BigDecimal availabilityRatio,
            BigDecimal capacityRatio,
            BigDecimal cycleTimeSeconds) {
        if (cycleTimeSeconds == null || cycleTimeSeconds.signum() <= 0) {
            throw new IllegalArgumentException("Cycle time must be positive.");
        }
        return nz(simulationHc)
                .multiply(workingHoursPerDay, MC)
                .multiply(SECONDS_PER_HOUR, MC)
                .multiply(nz(availabilityRatio), MC)
                .multiply(nz(capacityRatio), MC)
                .divide(cycleTimeSeconds, MC)
                .setScale(0, RoundingMode.FLOOR);
    }

    /** Daily OT production on working days (Excel ROUNDDOWN). */
    public static BigDecimal overtimeCapacity(
            boolean workingDay,
            BigDecimal simulationHc,
            BigDecimal maxOvertimeMinutes,
            BigDecimal availabilityRatio,
            BigDecimal capacityRatio,
            BigDecimal cycleTimeSeconds) {
        if (!workingDay || maxOvertimeMinutes == null || maxOvertimeMinutes.signum() <= 0) {
            return BigDecimal.ZERO.setScale(0, RoundingMode.FLOOR);
        }
        if (cycleTimeSeconds == null || cycleTimeSeconds.signum() <= 0) {
            throw new IllegalArgumentException("Cycle time must be positive.");
        }
        return nz(simulationHc)
                .multiply(maxOvertimeMinutes, MC)
                .multiply(SIXTY, MC)
                .multiply(nz(availabilityRatio), MC)
                .multiply(nz(capacityRatio), MC)
                .divide(cycleTimeSeconds, MC)
                .setScale(0, RoundingMode.FLOOR);
    }

    /** Backlog end = max(0, start + manual − std − ot). */
    public static BigDecimal backlogEnd(
            BigDecimal backlogStart, BigDecimal manualVolume, BigDecimal standardCapacity, BigDecimal overtimeCapacity) {
        BigDecimal end = nz(backlogStart)
                .add(nz(manualVolume), MC)
                .subtract(nz(standardCapacity), MC)
                .subtract(nz(overtimeCapacity), MC);
        if (end.signum() < 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return end.setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal applyManualMultipliers(
            BigDecimal forecastVolume,
            BigDecimal automationRatio,
            BigDecimal commercialRatio,
            BigDecimal dailyAdjustmentRatio) {
        return nz(forecastVolume)
                .multiply(BigDecimal.ONE.subtract(nz(automationRatio)), MC)
                .multiply(BigDecimal.ONE.add(nz(commercialRatio)), MC)
                .multiply(BigDecimal.ONE.add(nz(dailyAdjustmentRatio)), MC)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
