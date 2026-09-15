package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One Support Repository row: a Production Support activity on an APPROVED Exercise.
 * {@code exerciseUuid} opens Toolkit Info; {@code exerciseNo} is the business code.
 */
public record SupportRepositoryRow(
        String exerciseNo,
        UUID exerciseUuid,
        String center,
        String domain,
        String pl3,
        String toolkit,
        UUID categoryId,
        String standardCategory,
        String activity,
        String frequency,
        BigDecimal volume,
        String uom,
        BigDecimal fte,
        String comments,
        String validatedDate) {
}
