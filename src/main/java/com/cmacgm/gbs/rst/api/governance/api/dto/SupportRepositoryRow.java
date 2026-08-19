package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One Support Repository row: a Production Support activity on an APPROVED Exercise.
 */
public record SupportRepositoryRow(
        String exerciseNo,
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
        String submittedDate) {
}
