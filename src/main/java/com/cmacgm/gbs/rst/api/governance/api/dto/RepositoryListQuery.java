package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.time.LocalDate;

/**
 * RST Repository list filters (field values applied on the server).
 */
public record RepositoryListQuery(
        String exerciseCode,
        String center,
        String domain,
        String pl3Name,
        String toolkitName,
        String carrier,
        String site,
        String customerCountry,
        String sizingMonth,
        LocalDate validatedFrom,
        LocalDate validatedTo) {
}
