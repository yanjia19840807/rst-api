package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryRow;
import org.junit.jupiter.api.Test;

class SupportRepositoryFiltersTests {

    private static final UUID QUALITY = UUID.fromString("31000000-0000-0000-0000-000000000003");

    @Test
    void matchesAllWhenQueryIsEmpty() {
        assertThat(SupportRepositoryFilters.matches(
                row("GBS LEBANON", QUALITY, "Quality Control", "Bank Rec", "2026-03-10"),
                new SupportRepositoryQuery(null, null, null, null, null, null, null, null, null))).isTrue();
    }

    @Test
    void exactFiltersMustMatch() {
        SupportRepositoryRow row = row("GBS LEBANON", QUALITY, "Quality Control", "Bank Rec", "2026-03-10");
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, "GBS LEBANON", "Finance", "BANK RECONCILIATION", QUALITY, "Bank Rec", "2026-03", null, null)))
                .isTrue();
        assertThat(SupportRepositoryFilters.matches(
                row, query("EX-2", null, null, null, null, null, null, null, null))).isFalse();
        assertThat(SupportRepositoryFilters.matches(
                row, query("ex-1", null, null, null, null, null, null, null, null))).isTrue();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, "GBS INDIA", null, null, null, null, null, null, null))).isFalse();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, "Customer Care", null, null, null, null, null, null))).isFalse();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, null, "BOOKING AMENDMENTS", null, null, null, null, null))).isFalse();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, null, null, UUID.fromString("31000000-0000-0000-0000-000000000004"), null, null, null, null)))
                .isFalse();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, null, null, null, "Other Toolkit", null, null, null))).isFalse();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, null, null, null, null, "2026-02", null, null))).isFalse();
    }

    @Test
    void validatedDateIsInclusive() {
        SupportRepositoryRow row = row("GBS LEBANON", QUALITY, "Quality Control", "Bank Rec", "2026-03-10");
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, null, null, null, null, null, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, null, null, null, null, null, LocalDate.parse("2026-03-11"), null)))
                .isFalse();
        assertThat(SupportRepositoryFilters.matches(
                row, query(null, null, null, null, null, null, null, null, LocalDate.parse("2026-03-09"))))
                .isFalse();
        SupportRepositoryRow timed = row("GBS LEBANON", QUALITY, "Quality Control", "Bank Rec", "2026-03-10T16:45:00");
        assertThat(SupportRepositoryFilters.matches(
                timed, query(null, null, null, null, null, null, null, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
    }

    @Test
    void distinctIgnoresBlankAndSorts() {
        List<String> names = SupportRepositoryFilters.distinct(
                List.of(
                        row("GBS INDIA", QUALITY, "Reporting", "A", "2026-01-01"),
                        row("GBS LEBANON", QUALITY, "Reporting", "B", "2026-01-01"),
                        row("", QUALITY, "Reporting", "C", "2026-01-01"),
                        row("GBS LEBANON", QUALITY, "Reporting", "D", "2026-01-01")),
                SupportRepositoryRow::center);
        assertThat(names).containsExactly("GBS INDIA", "GBS LEBANON");
    }

    private static SupportRepositoryQuery query(
            String exerciseCode,
            String center,
            String domain,
            String pl3Name,
            UUID categoryId,
            String toolkitName,
            String sizingMonth,
            LocalDate validatedFrom,
            LocalDate validatedTo) {
        return new SupportRepositoryQuery(
                exerciseCode, center, domain, pl3Name, categoryId, toolkitName, sizingMonth, validatedFrom, validatedTo);
    }

    private static SupportRepositoryRow row(
            String center, UUID categoryId, String category, String toolkit, String validatedDate) {
        return new SupportRepositoryRow(
                "EX-1",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                center,
                "Finance",
                "BANK RECONCILIATION",
                toolkit,
                categoryId,
                category,
                "Case audit",
                "Weekly",
                BigDecimal.ONE,
                "Cases",
                BigDecimal.ONE,
                "",
                "2026-03",
                validatedDate);
    }
}
