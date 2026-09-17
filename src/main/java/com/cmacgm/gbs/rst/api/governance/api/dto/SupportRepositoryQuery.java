package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Support Repository list filters (field values applied on the server).
 */
public record SupportRepositoryQuery(
        String exerciseCode,
        String center,
        String domain,
        String pl3Name,
        UUID categoryId,
        String toolkitName,
        String sizingMonth,
        LocalDate validatedFrom,
        LocalDate validatedTo) {
}
