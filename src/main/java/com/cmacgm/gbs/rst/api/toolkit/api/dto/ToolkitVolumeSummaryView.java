package com.cmacgm.gbs.rst.api.toolkit.api.dto;

import java.time.LocalDate;

/**
 * Canonical Toolkit volume coverage (not the current Exercise window).
 */
public record ToolkitVolumeSummaryView(
        int monthlyCount,
        String monthlyFrom,
        String monthlyTo,
        int dailyCount,
        LocalDate dailyFrom,
        LocalDate dailyTo) {
}
