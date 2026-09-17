package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;

/**
 * One GBS Center completion and aging row, weighted by Delivery HC.
 */
public record DashboardCenterRow(
        String center,
        BigDecimal applicableHc,
        BigDecimal completedThisQuarter,
        String completionPct,
        BigDecimal completed3To6Months,
        BigDecimal neverDone,
        BigDecimal completed6To12Months,
        BigDecimal completedOver1Year,
        boolean onTrack) {
}
