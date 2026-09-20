package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryListQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryRow;
import org.junit.jupiter.api.Test;

class RepositoryRowFiltersTests {

    @Test
    void matchesAllWhenQueryIsEmpty() {
        assertThat(RepositoryRowFilters.matches(row("EX-1", "Shanghai", "OPS", "PL3-A", "TK-1", "2026-06", "2026-03-01"),
                new RepositoryListQuery(null, null, null, null, null, null, null, null, null, null, null))).isTrue();
    }

    @Test
    void exerciseCodeIsCaseInsensitiveContains() {
        RepositoryRow row = row("EX-ABC-01", "Shanghai", "OPS", "PL3-A", "TK-1", "2026-06", "2026-03-01");
        assertThat(RepositoryRowFilters.matches(
                row, query("abc", null, null, null, null, null, null, null, null, null, null))).isTrue();
        assertThat(RepositoryRowFilters.matches(
                row, query("zzz", null, null, null, null, null, null, null, null, null, null))).isFalse();
    }

    @Test
    void exactFiltersMustMatch() {
        RepositoryRow row = row("EX-1", "Shanghai", "OPS", "PL3-A", "TK-1", "2026-06", "2026-03-01");
        assertThat(RepositoryRowFilters.matches(
                row, query(null, "Shanghai", "OPS", "PL3-A", "TK-1", "CMA", "SITE", "FR", "2026-06", null, null)))
                .isTrue();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, "Ningbo", null, null, null, null, null, null, null, null, null))).isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, "FIN", null, null, null, null, null, null, null, null))).isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, "PL3-B", null, null, null, null, null, null, null))).isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, "TK-2", null, null, null, null, null, null))).isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, "MSC", null, null, null, null, null))).isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, null, "OTHER", null, null, null, null))).isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, null, null, "CN", null, null, null))).isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, "2026-05", null, null))).isFalse();
    }

    @Test
    void validatedDateIsInclusive() {
        RepositoryRow row = row("EX-1", "Shanghai", "OPS", "PL3-A", "TK-1", "2026-06", "2026-03-10");
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, null, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, null, LocalDate.parse("2026-03-11"), null)))
                .isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, null, null, LocalDate.parse("2026-03-09"))))
                .isFalse();
        RepositoryRow timed = row("EX-1", "Shanghai", "OPS", "PL3-A", "TK-1", "2026-06", "2026-03-10T16:45:00");
        assertThat(RepositoryRowFilters.matches(
                timed, query(null, null, null, null, null, null, null, null, null, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
    }

    @Test
    void customerCountryFilterMatchesCommaTokens() {
        RepositoryRow row = new RepositoryRow(
                "EX-1",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "CMA",
                "SITE",
                "Shanghai",
                "OPS",
                "PL1",
                "PL2",
                "PL3-A",
                "TK-1",
                "HONG KONG SAR, CHINA",
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "",
                "2026-06",
                "2026-03-01");
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, null, null, "CHINA", null, null, null))).isTrue();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, null, null, "HONG KONG SAR", null, null, null)))
                .isTrue();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, null, null, "FRANCE", null, null, null))).isFalse();
    }

    @Test
    void distinctIgnoresBlankAndSorts() {
        List<String> names = RepositoryRowFilters.distinct(
                List.of(
                        row("EX-1", "Ningbo", "OPS", "B", "TK", "2026-06", "2026-01-01"),
                        row("EX-2", "Shanghai", "OPS", "A", "TK", "2026-06", "2026-01-01"),
                        row("EX-3", "", "OPS", "A", "TK", "2026-06", "2026-01-01"),
                        row("EX-4", "Shanghai", "OPS", "A", "TK", "2026-06", "2026-01-01")),
                RepositoryRow::country);
        assertThat(names).containsExactly("Ningbo", "Shanghai");
    }

    private static RepositoryListQuery query(
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
        return new RepositoryListQuery(
                exerciseCode,
                center,
                domain,
                pl3Name,
                toolkitName,
                carrier,
                site,
                customerCountry,
                sizingMonth,
                validatedFrom,
                validatedTo);
    }

    private static RepositoryRow row(
            String exerciseId,
            String country,
            String domain,
            String pl3,
            String toolkit,
            String sizingMonth,
            String validatedDate) {
        return new RepositoryRow(
                exerciseId,
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "CMA",
                "SITE",
                country,
                domain,
                "PL1",
                "PL2",
                pl3,
                toolkit,
                "FR",
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "",
                sizingMonth,
                validatedDate);
    }
}
