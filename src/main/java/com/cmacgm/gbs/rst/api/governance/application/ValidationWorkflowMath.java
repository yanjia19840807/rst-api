package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import com.cmacgm.gbs.rst.api.exercise.scenario.application.sizing.SizingMath;

/**
 * Exercise-level Capacity Creation and % for Validation Workflow.
 */
public final class ValidationWorkflowMath {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private ValidationWorkflowMath() {
    }

    /**
     * Capacity Creation when Official RS exists: Actual HC − RS − Support. Otherwise empty.
     *
     * @param actualHc Team Setup TotalAgent, else Shared KPI Delivery HC
     * @param rightSizingHc Official Scenario RIGHT_SIZING_HC; null leaves Capacity empty
     * @param productionSupportFte Exercise Support FTE total
     * @return Capacity Creation, or null when RS is missing
     */
    public static BigDecimal capacityCreation(
            BigDecimal actualHc, BigDecimal rightSizingHc, BigDecimal productionSupportFte) {
        if (rightSizingHc == null) {
            return null;
        }
        return SizingMath.capacityCreation(actualHc, rightSizingHc, productionSupportFte);
    }

    /**
     * Capacity / Actual HC as a percent (one decimal). Empty when Capacity or Actual HC is missing.
     *
     * @param capacityCreation Exercise Capacity Creation
     * @param actualHc Team Setup TotalAgent, else Shared KPI Delivery HC
     * @return percent, or null
     */
    public static BigDecimal capacityPct(BigDecimal capacityCreation, BigDecimal actualHc) {
        if (capacityCreation == null || actualHc == null || actualHc.signum() <= 0) {
            return null;
        }
        return capacityCreation.multiply(HUNDRED, MC).divide(actualHc, 1, RoundingMode.HALF_UP);
    }
}
