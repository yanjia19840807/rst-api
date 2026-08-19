package com.cmacgm.gbs.rst.api.governance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.cmacgm.gbs.rst.api.governance.api.dto.SupportCategorySummary;
import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryRow;

/**
 * Aggregates filtered Support Repository rows into totals and category mix.
 */
public final class SupportRepositoryMath {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private SupportRepositoryMath() {
    }

    /**
     * Rolls filtered activity rows into Total FTE, top category, and category table.
     *
     * @param items filtered support rows
     * @return summary aligned with the same row set
     */
    public static Summary summarize(List<SupportRepositoryRow> items) {
        if (items == null || items.isEmpty()) {
            return new Summary(ZERO, "", null, List.of());
        }
        Map<String, BigDecimal> fteByCategory = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SupportRepositoryRow row : items) {
            BigDecimal fte = row.fte() == null ? BigDecimal.ZERO : row.fte();
            total = total.add(fte);
            String category = blankToEmpty(row.standardCategory());
            fteByCategory.merge(category, fte, BigDecimal::add);
        }
        BigDecimal scaledTotal = total.setScale(2, RoundingMode.HALF_UP);
        List<SupportCategorySummary> summaries = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : fteByCategory.entrySet()) {
            BigDecimal categoryFte = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            summaries.add(new SupportCategorySummary(
                    entry.getKey(),
                    categoryFte,
                    percent(entry.getValue(), scaledTotal)));
        }
        summaries.sort(Comparator
                .comparing(SupportCategorySummary::supportFte, Comparator.reverseOrder())
                .thenComparing(SupportCategorySummary::category, String.CASE_INSENSITIVE_ORDER));
        if (summaries.isEmpty()) {
            return new Summary(scaledTotal, "", null, List.of());
        }
        SupportCategorySummary top = summaries.getFirst();
        return new Summary(scaledTotal, top.category(), top.supportFte(), List.copyOf(summaries));
    }

    /**
     * Maps stored frequency codes to repository display labels.
     *
     * @param frequencyCode DAILY / WEEKLY / MONTHLY
     * @return title-case label
     */
    public static String frequencyLabel(String frequencyCode) {
        String code = frequencyCode == null ? "" : frequencyCode.trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "DAILY", "DAY" -> "Daily";
            case "WEEKLY", "WEEK" -> "Weekly";
            case "MONTHLY", "MONTH" -> "Monthly";
            default -> frequencyCode == null ? "" : frequencyCode;
        };
    }

    private static String percent(BigDecimal part, BigDecimal total) {
        if (total.signum() <= 0) {
            return "0.0%";
        }
        return part.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Totals and category mix for one filtered row set.
     *
     * @param totalSupportFte sum of row FTE, 2 dp
     * @param topCategory category with the largest FTE
     * @param topCategoryFte that category's FTE
     * @param categorySummaries categories ordered by FTE descending
     */
    public record Summary(
            BigDecimal totalSupportFte,
            String topCategory,
            BigDecimal topCategoryFte,
            List<SupportCategorySummary> categorySummaries) {
    }
}
