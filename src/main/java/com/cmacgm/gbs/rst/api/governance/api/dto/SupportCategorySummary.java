package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;

/**
 * Support FTE rolled up by standard category for the current filter.
 */
public record SupportCategorySummary(
        String category,
        BigDecimal supportFte,
        String pctOfSupport) {
}
