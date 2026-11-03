package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * RST Repository list filters (field values applied on the server).
 * Empty country, region, and cluster lists mean that filter is not applied.
 */
public record RepositoryListQuery(
        String exerciseCode,
        String center,
        String domain,
        String pl3Name,
        String toolkitName,
        String carrier,
        String site,
        List<String> customerCountries,
        List<String> regions,
        List<String> clusters,
        String sizingMonth,
        LocalDate validatedFrom,
        LocalDate validatedTo) {
}
