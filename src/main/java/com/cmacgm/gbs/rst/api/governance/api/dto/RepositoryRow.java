package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One RST Repository row: a frozen Shared KPI line on an APPROVED Exercise.
 * {@code exerciseId} is the business code shown in the table; {@code exerciseUuid}
 * is the Exercise id used to open Toolkit Info. {@code submissionId} opens the
 * read-only Submission Review.
 */
public record RepositoryRow(
        String exerciseId,
        UUID exerciseUuid,
        UUID submissionId,
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
        String sizingMonth,
        String validatedDate) {
}
