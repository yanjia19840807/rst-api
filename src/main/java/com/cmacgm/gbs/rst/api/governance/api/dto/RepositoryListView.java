package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * RST Repository list: one page of filtered rows, unfiltered dropdown options,
 * and HC totals from all filtered rows.
 */
public record RepositoryListView(
        List<RepositoryRow> items,
        int page,
        int pageSize,
        long total,
        int totalPages,
        List<String> centers,
        List<String> domains,
        List<String> pl3Names,
        List<String> toolkitNames,
        List<String> carriers,
        List<String> sites,
        List<String> customerCountries,
        BigDecimal totalDeliveryHc,
        BigDecimal totalRightSizingHc,
        BigDecimal totalSupport,
        BigDecimal totalCapacityCreation) {
}
