package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.util.List;

/**
 * RST Repository list: one page of filtered rows plus unfiltered dropdown options.
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
        List<String> toolkitNames) {
}
