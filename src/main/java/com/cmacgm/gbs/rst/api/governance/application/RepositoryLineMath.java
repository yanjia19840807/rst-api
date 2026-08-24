package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import com.cmacgm.gbs.rst.api.scenario.application.sizing.SizingMath;

/**
 * Allocates Exercise-level RS / Support / Capacity onto a Shared KPI line by Delivery HC share.
 */
public final class RepositoryLineMath {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private RepositoryLineMath() {
    }

    /**
     * Splits Exercise totals onto one KPI line: {@code line = total × lineDelivery / Σ delivery}.
     *
     * @param lineDeliveryHc this line's frozen Delivery HC
     * @param totalDeliveryHc sum of Delivery HC on the Exercise
     * @param rightSizingHc Official Scenario RIGHT_SIZING_HC; null leaves RS / Capacity empty
     * @param productionSupportFte Exercise Support FTE total
     * @return allocated line metrics
     */
    public static LineMetrics allocate(
            BigDecimal lineDeliveryHc,
            BigDecimal totalDeliveryHc,
            BigDecimal rightSizingHc,
            BigDecimal productionSupportFte) {
        return allocate(lineDeliveryHc, totalDeliveryHc, totalDeliveryHc, rightSizingHc, productionSupportFte);
    }

    /**
     * Splits Exercise totals onto one KPI line. Capacity uses Actual HC (TotalAgent).
     *
     * @param lineDeliveryHc this line's frozen Delivery HC
     * @param totalDeliveryHc sum of Delivery HC on the Exercise
     * @param actualHc Team Setup TotalAgent, else Delivery HC
     * @param rightSizingHc Official Scenario RIGHT_SIZING_HC; null leaves RS / Capacity empty
     * @param productionSupportFte Exercise Support FTE total
     * @return allocated line metrics
     */
    public static LineMetrics allocate(
            BigDecimal lineDeliveryHc,
            BigDecimal totalDeliveryHc,
            BigDecimal actualHc,
            BigDecimal rightSizingHc,
            BigDecimal productionSupportFte) {
        BigDecimal lineHc = nz(lineDeliveryHc);
        BigDecimal totalHc = nz(totalDeliveryHc);
        if (totalHc.signum() <= 0) {
            return new LineMetrics(lineHc, null, null, null, null);
        }
        BigDecimal weight = lineHc.divide(totalHc, MC);
        BigDecimal lineSupport = productionSupportFte == null
                ? null
                : scale(productionSupportFte.multiply(weight, MC));
        if (rightSizingHc == null) {
            return new LineMetrics(lineHc, null, lineSupport, null, null);
        }
        if (productionSupportFte == null) {
            return new LineMetrics(
                    lineHc, scale(rightSizingHc.multiply(weight, MC)), null, null, null);
        }
        BigDecimal supportTotal = productionSupportFte;
        BigDecimal headcount = SizingMath.actualHeadcount(actualHc, totalHc);
        BigDecimal capacityTotal = SizingMath.capacityCreation(headcount, rightSizingHc, supportTotal);
        BigDecimal lineRs = scale(rightSizingHc.multiply(weight, MC));
        BigDecimal lineCapacity = scale(capacityTotal.multiply(weight, MC));
        BigDecimal pct = capacityTotal.multiply(HUNDRED, MC)
                .divide(headcount, 1, RoundingMode.HALF_UP);
        return new LineMetrics(lineHc, lineRs, lineSupport, lineCapacity, pct);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Allocated KPI-line metrics.
     *
     * @param deliveryHc frozen line Delivery HC
     * @param rightSizingHc allocated RS HC
     * @param productionSupport allocated Support FTE
     * @param capacityCreation allocated Capacity Creation
     * @param capacityPct Exercise Capacity / Delivery HC as a percent (one decimal)
     */
    public record LineMetrics(
            BigDecimal deliveryHc,
            BigDecimal rightSizingHc,
            BigDecimal productionSupport,
            BigDecimal capacityCreation,
            BigDecimal capacityPct) {
    }
}
