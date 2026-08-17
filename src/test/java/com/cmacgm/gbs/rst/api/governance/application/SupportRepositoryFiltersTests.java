package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryRow;
import org.junit.jupiter.api.Test;

class SupportRepositoryFiltersTests {

    @Test
    void matchesAllWhenQueryIsEmpty() {
        assertThat(SupportRepositoryFilters.matches(
                row("GBS China", "Quality Control", "Bank Rec", "2026-03-10"),
                new SupportRepositoryQuery(null, null, null, null, null))).isTrue();
    }

    @Test
    void exactFiltersMustMatch() {
        SupportRepositoryRow row = row("GBS China", "Quality Control", "Bank Rec", "2026-03-10");
        assertThat(SupportRepositoryFilters.matches(
                row, query("GBS China", "Quality Control", "Bank Rec", null, null))).isTrue();
        assertThat(SupportRepositoryFilters.matches(
                row, query("GBS India", null, null, null, null))).isFalse();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, "Reporting", null, null, null))).isFalse();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, "Other Toolkit", null, null))).isFalse();
    }

    @Test
    void submittedDateIsInclusive() {
        SupportRepositoryRow row = row("GBS China", "Quality Control", "Bank Rec", "2026-03-10");
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, null, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, null, LocalDate.parse("2026-03-11"), null)))
                .isFalse();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, null, null, LocalDate.parse("2026-03-09"))))
                .isFalse();
    }

    @Test
    void distinctIgnoresBlankAndSorts() {
        List<String> names = SupportRepositoryFilters.distinct(
                List.of(
                        row("GBS India", "Reporting", "A", "2026-01-01"),
                        row("GBS China", "Reporting", "B", "2026-01-01"),
                        row("", "Reporting", "C", "2026-01-01"),
                        row("GBS China", "Reporting", "D", "2026-01-01")),
                SupportRepositoryRow::center);
        assertThat(names).containsExactly("GBS China", "GBS India");
    }

    private static SupportRepositoryQuery query(
            String center,
            String category,
            String toolkitName,
            LocalDate submittedFrom,
            LocalDate submittedTo) {
        return new SupportRepositoryQuery(center, category, toolkitName, submittedFrom, submittedTo);
    }

    private static SupportRepositoryRow row(
            String center, String category, String toolkit, String submittedDate) {
        return new SupportRepositoryRow(
                "EX-1",
                center,
                "Finance",
                "BANK RECONCILIATION",
                toolkit,
                category,
                "Case audit",
                "Weekly",
                BigDecimal.ONE,
                "Cases",
                BigDecimal.ONE,
                "",
                submittedDate);
    }
}
