package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.time.LocalDate;

/**
 * Support Repository list filters (field values applied on the server).
 */
public record SupportRepositoryQuery(
        String center,
        String category,
        String toolkitName,
        LocalDate submittedFrom,
        LocalDate submittedTo) {
}
