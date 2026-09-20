package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One Support Repository row: a Production Support activity on an APPROVED Exercise.
 * Shared KPI scope is copied from the Exercise (not split by KPI line).
 * {@code exerciseUuid} opens Toolkit Info; {@code exerciseNo} is the business code.
 */
public record SupportRepositoryRow(
        String exerciseNo,
        UUID exerciseUuid,
        String center,
        String domain,
        String pl1,
        String pl2,
        String pl3,
        String toolkit,
        List<String> carriers,
        List<String> sites,
        List<String> customerCountries,
        UUID categoryId,
        String standardCategory,
        String activity,
        String frequency,
        BigDecimal volume,
        String uom,
        BigDecimal fte,
        String comments,
        String sizingMonth,
        String validatedDate) {
}
