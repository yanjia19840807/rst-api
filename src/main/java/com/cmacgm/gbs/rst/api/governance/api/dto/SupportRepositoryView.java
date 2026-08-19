package com.cmacgm.gbs.rst.api.governance.api.dto;

import java.math.BigDecimal;
import java.util.List;

import com.cmacgm.gbs.rst.api.supporttaxonomy.api.dto.SupportTaxonomyOption;

/**
 * Support Repository: one page of activity rows, summaries from all filtered rows, and unfiltered options.
 */
public record SupportRepositoryView(
        BigDecimal totalSupportFte,
        String topCategory,
        BigDecimal topCategoryFte,
        List<SupportCategorySummary> categorySummaries,
        List<SupportRepositoryRow> items,
        int page,
        int pageSize,
        long total,
        int totalPages,
        List<String> centers,
        List<SupportTaxonomyOption> categories,
        List<String> toolkitNames) {
}
