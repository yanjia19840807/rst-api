package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Global Dashboard: header cards, GBS aging table, and domain drill-down by center.
 */
public record DashboardView(
        List<DashboardMetric> metrics,
        List<DashboardCenterRow> centers,
        Map<String, List<DashboardDomainRow>> domainsByCenter) {
}
