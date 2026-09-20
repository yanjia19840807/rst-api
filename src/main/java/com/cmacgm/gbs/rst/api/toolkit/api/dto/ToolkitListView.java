package com.cmacgm.gbs.rst.api.toolkit.api.dto;

import java.util.List;

/**
 * Supervisor Toolkit list: one page of filtered rows and unfiltered scope options.
 */
public record ToolkitListView(
        List<ToolkitResponse> items,
        int page,
        int pageSize,
        long total,
        int totalPages,
        List<String> pl3Names,
        List<String> centers,
        List<String> domains,
        List<ToolkitPl3Option> pl3s,
        List<String> carriers,
        List<String> sites,
        List<String> customerCountries) {
}
