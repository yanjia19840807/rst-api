package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import com.cmacgm.gbs.rst.api.scenario.application.sizing.SizingMath;

/**
 * Exercise-level Capacity Creation and % for Validation Workflow.
 */
public final class ValidationWorkflowMath {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private ValidationWorkflowMath() {
    }

    /**
     * Capacity Creation when Official RS exists: Delivery − RS − Support. Otherwise empty.
     *
     * @param deliveryHc Shared KPI Delivery HC total
     * @param rightSizingHc Official Scenario RIGHT_SIZING_HC; null leaves Capacity empty
     * @param productionSupportFte Exercise Support FTE total
     * @return Capacity Creation, or null when RS is missing
     */
    public static BigDecimal capacityCreation(
            BigDecimal deliveryHc, BigDecimal rightSizingHc, BigDecimal productionSupportFte) {
        if (rightSizingHc == null) {
            return null;
        }
        return SizingMath.capacityCreation(deliveryHc, rightSizingHc, productionSupportFte);
    }

    /**
     * Capacity / Delivery HC as a percent (one decimal). Empty when Capacity or Delivery is missing.
     *
     * @param capacityCreation Exercise Capacity Creation
     * @param deliveryHc Shared KPI Delivery HC total
     * @return percent, or null
     */
    public static BigDecimal capacityPct(BigDecimal capacityCreation, BigDecimal deliveryHc) {
        if (capacityCreation == null || deliveryHc == null || deliveryHc.signum() <= 0) {
            return null;
        }
        return capacityCreation.multiply(HUNDRED, MC).divide(deliveryHc, 1, RoundingMode.HALF_UP);
    }
}
