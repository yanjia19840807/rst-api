package com.cmacgm.gbs.rst.api.governance.api.dto;

/**
 * One GBS Center completion and aging row.
 */
public record DashboardCenterRow(
        String center,
        int applicablePl3,
        int completedThisQuarter,
        String completionPct,
        int completed3To6Months,
        int neverDone,
        int completed6To12Months,
        int completedOver1Year,
        boolean onTrack) {
}
