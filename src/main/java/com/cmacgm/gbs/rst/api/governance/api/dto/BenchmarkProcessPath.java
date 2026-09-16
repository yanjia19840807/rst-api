package com.cmacgm.gbs.rst.api.governance.api.dto;

/**
 * One Domain → PL1 → PL2 → PL3 path from APPROVED Shared KPI lines.
 */
public record BenchmarkProcessPath(
        String domain,
        String pl1,
        String pl2,
        String pl3Code,
        String pl3Name) {
}
