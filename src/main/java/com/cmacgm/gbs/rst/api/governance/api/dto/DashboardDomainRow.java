package com.cmacgm.gbs.rst.api.governance.api.dto;

/**
 * Domain drill-down row for one GBS Center.
 */
public record DashboardDomainRow(
        String domain,
        int applicablePl3,
        int completed,
        String pct,
        int neverDone) {
}
