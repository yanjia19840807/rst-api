package com.cmacgm.gbs.rst.api.scenario.application.sizing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Workbook §11.2 sizing formulas (monthly + daily).
 */
public final class SizingMath {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal SECONDS_PER_HOUR = new BigDecimal("3600");
    private static final BigDecimal SIXTY = new BigDecimal("60");

    private SizingMath() {
    }

    /** Monthly / shared: Forecast × (1 − Auto). */
    public static BigDecimal monthlyManualVolume(BigDecimal forecastVolume, BigDecimal automationRatio) {
        return forecastVolume
                .multiply(BigDecimal.ONE.subtract(nz(automationRatio)), MC)
                .setScale(6, RoundingMode.HALF_UP);
    }

    /** Daily: same manual volume base as monthly. */
    public static BigDecimal dailyManualVolume(BigDecimal forecastVolume, BigDecimal automationRatio) {
        return monthlyManualVolume(forecastVolume, automationRatio);
    }

    /**
     * Nominal HC without overtime (RoundUp to integer).
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
        return raw.setScale(0, RoundingMode.UP);
    }

    /**
     * Nominal HC with overtime (RoundUp to integer).
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
        return raw.setScale(0, RoundingMode.UP);
    }

    /** Capacity creation: Delivery − RS HC − Support. */
    public static BigDecimal capacityCreation(
            BigDecimal deliveryHc, BigDecimal rightSizingHc, BigDecimal productionSupportFte) {
        return nz(deliveryHc)
                .subtract(nz(rightSizingHc), MC)
                .subtract(nz(productionSupportFte), MC)
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

    /** Daily standard production capacity. */
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
                .setScale(6, RoundingMode.HALF_UP);
    }

    /** Daily OT production (working days only). */
    public static BigDecimal overtimeCapacity(
            boolean workingDay,
            BigDecimal simulationHc,
            BigDecimal maxOvertimeMinutes,
            BigDecimal availabilityRatio,
            BigDecimal capacityRatio,
            BigDecimal cycleTimeSeconds) {
        if (!workingDay || maxOvertimeMinutes == null || maxOvertimeMinutes.signum() <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
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
                .setScale(6, RoundingMode.HALF_UP);
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

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
