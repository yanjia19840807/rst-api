package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;

/**
 * Domain drill-down row for one GBS Center, weighted by Delivery HC.
 */
public record DashboardDomainRow(
        String domain,
        BigDecimal applicableHc,
        BigDecimal completed,
        String pct,
        BigDecimal neverDone) {
}
