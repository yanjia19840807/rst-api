package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryListQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryRow;
import org.junit.jupiter.api.Test;

class RepositoryRowFiltersTests {

    @Test
    void matchesAllWhenQueryIsEmpty() {
        assertThat(RepositoryRowFilters.matches(row("EX-1", "Shanghai", "OPS", "PL3-A", "TK-1", "2026-03-01"),
                new RepositoryListQuery(null, null, null, null, null, null, null))).isTrue();
    }

    @Test
    void exerciseCodeIsCaseInsensitiveContains() {
        RepositoryRow row = row("EX-ABC-01", "Shanghai", "OPS", "PL3-A", "TK-1", "2026-03-01");
        assertThat(RepositoryRowFilters.matches(
                row, query("abc", null, null, null, null, null, null))).isTrue();
        assertThat(RepositoryRowFilters.matches(
                row, query("zzz", null, null, null, null, null, null))).isFalse();
    }

    @Test
    void exactFiltersMustMatch() {
        RepositoryRow row = row("EX-1", "Shanghai", "OPS", "PL3-A", "TK-1", "2026-03-01");
        assertThat(RepositoryRowFilters.matches(
                row, query(null, "Shanghai", "OPS", "PL3-A", "TK-1", null, null))).isTrue();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, "Ningbo", null, null, null, null, null))).isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, "FIN", null, null, null, null))).isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, "PL3-B", null, null, null))).isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, "TK-2", null, null))).isFalse();
    }

    @Test
    void submittedDateIsInclusive() {
        RepositoryRow row = row("EX-1", "Shanghai", "OPS", "PL3-A", "TK-1", "2026-03-10");
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, LocalDate.parse("2026-03-11"), null)))
                .isFalse();
        assertThat(RepositoryRowFilters.matches(
                row, query(null, null, null, null, null, null, LocalDate.parse("2026-03-09"))))
                .isFalse();
    }

    @Test
    void distinctIgnoresBlankAndSorts() {
        List<String> names = RepositoryRowFilters.distinct(
                List.of(
                        row("EX-1", "Ningbo", "OPS", "B", "TK", "2026-01-01"),
                        row("EX-2", "Shanghai", "OPS", "A", "TK", "2026-01-01"),
                        row("EX-3", "", "OPS", "A", "TK", "2026-01-01"),
                        row("EX-4", "Shanghai", "OPS", "A", "TK", "2026-01-01")),
                RepositoryRow::country);
        assertThat(names).containsExactly("Ningbo", "Shanghai");
    }

    private static RepositoryListQuery query(
            String exerciseCode,
            String center,
            String domain,
            String pl3Name,
            String toolkitName,
            LocalDate submittedFrom,
            LocalDate submittedTo) {
        return new RepositoryListQuery(
                exerciseCode, center, domain, pl3Name, toolkitName, submittedFrom, submittedTo);
    }

    private static RepositoryRow row(
            String exerciseId,
            String country,
            String domain,
            String pl3,
            String toolkit,
            String submittedDate) {
        return new RepositoryRow(
                exerciseId,
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
                submittedDate);
    }
}
