package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Support Repository list filters (field values applied on the server).
 */
public record SupportRepositoryQuery(
        String center,
        UUID categoryId,
        String toolkitName,
        LocalDate submittedFrom,
        LocalDate submittedTo) {
}
