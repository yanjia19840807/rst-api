package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;

/**
 * One RST Repository row: a frozen Shared KPI line on an APPROVED Exercise.
 */
public record RepositoryRow(
        String exerciseId,
        String carrier,
        String site,
        String country,
        String domain,
        String pl1,
        String pl2,
        String pl3,
        String toolkit,
        String kpi,
        BigDecimal deliveryHc,
        BigDecimal rsHc,
        BigDecimal support,
        BigDecimal capacityCreation,
        BigDecimal capacityPct,
        String volumeYoY,
        String submittedDate) {
}
