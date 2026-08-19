package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.cmacgm.gbs.rst.api.governance.api.dto.SupportCategorySummary;
import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryRow;
import org.junit.jupiter.api.Test;

class SupportRepositoryMathTests {

    @Test
    void emptyRowsYieldZeroTotal() {
        SupportRepositoryMath.Summary summary = SupportRepositoryMath.summarize(List.of());
        assertThat(summary.totalSupportFte()).isEqualByComparingTo("0.00");
        assertThat(summary.topCategory()).isEmpty();
        assertThat(summary.topCategoryFte()).isNull();
        assertThat(summary.categorySummaries()).isEmpty();
    }

    @Test
    void rollsUpByCategory() {
        SupportRepositoryMath.Summary summary = SupportRepositoryMath.summarize(List.of(
                row("Quality Control", "Case audit", "0.51"),
                row("Quality Control", "Spot check", "0.20"),
                row("Reporting", "SKPI pack", "0.09")));

        assertThat(summary.totalSupportFte()).isEqualByComparingTo("0.80");
        assertThat(summary.topCategory()).isEqualTo("Quality Control");
        assertThat(summary.topCategoryFte()).isEqualByComparingTo("0.71");
        assertThat(summary.categorySummaries()).extracting(SupportCategorySummary::category)
                .containsExactly("Quality Control", "Reporting");
        assertThat(summary.categorySummaries().getFirst().pctOfSupport()).isEqualTo("88.8%");
        assertThat(summary.categorySummaries().get(1).pctOfSupport()).isEqualTo("11.3%");
    }

    @Test
    void frequencyLabelMapsCodes() {
        assertThat(SupportRepositoryMath.frequencyLabel("DAILY")).isEqualTo("Daily");
        assertThat(SupportRepositoryMath.frequencyLabel("weekly")).isEqualTo("Weekly");
        assertThat(SupportRepositoryMath.frequencyLabel("MONTHLY")).isEqualTo("Monthly");
    }

    private static SupportRepositoryRow row(String category, String activity, String fte) {
        return new SupportRepositoryRow(
                "EX-1",
                "GBS China",
                "Finance",
                "BANK RECONCILIATION",
                "Bank Rec",
                null,
                category,
                activity,
                "Weekly",
                BigDecimal.ONE,
                "Cases",
                new BigDecimal(fte),
                "",
                "2026-03-10");
    }
}
