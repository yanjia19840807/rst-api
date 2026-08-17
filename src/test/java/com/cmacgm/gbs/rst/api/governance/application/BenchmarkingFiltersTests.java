package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkPl3Option;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkingQuery;
import org.junit.jupiter.api.Test;

class BenchmarkingFiltersTests {

    @Test
    void requiresPl3Code() {
        BenchmarkRow row = row("PL3-BANK", "GBS China", "FINANCE", "R2R", "Bank Rec", "2026-03-10");
        assertThat(BenchmarkingFilters.matches(row, query(null, null, null, null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(row, query(null, null, null, null, "PL3-BANK", null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(row, query(null, null, null, null, "PL3-OTHER", null, null)))
                .isFalse();
    }

    @Test
    void exactFiltersMustMatch() {
        BenchmarkRow row = row("PL3-BANK", "GBS China", "FINANCE", "R2R", "Bank Rec", "2026-03-10");
        assertThat(BenchmarkingFilters.matches(
                row, query("GBS China", "FINANCE", "R2R", "Bank Rec", "PL3-BANK", null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(
                row, query("GBS India", null, null, null, "PL3-BANK", null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, "OPS", null, null, "PL3-BANK", null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, "P2P", null, "PL3-BANK", null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, "AP", "PL3-BANK", null, null)))
                .isFalse();
    }

    @Test
    void submittedDateIsInclusive() {
        BenchmarkRow row = row("PL3-BANK", "GBS China", "FINANCE", "R2R", "Bank Rec", "2026-03-10");
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, "PL3-BANK",
                        LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, "PL3-BANK", LocalDate.parse("2026-03-11"), null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, "PL3-BANK", null, LocalDate.parse("2026-03-09"))))
                .isFalse();
    }

    @Test
    void distinctPl3KeepsCodeAndSortsByName() {
        List<BenchmarkPl3Option> options = BenchmarkingFilters.distinctPl3(List.of(
                row("PL3-B", "GBS China", "FINANCE", "R2R", "Zebra", "2026-01-01"),
                row("PL3-A", "GBS India", "FINANCE", "R2R", "Alpha", "2026-01-01"),
                row("PL3-A", "GBS India", "FINANCE", "R2R", "Alpha", "2026-01-01"),
                row("", "GBS India", "FINANCE", "R2R", "Missing", "2026-01-01")));
        assertThat(options).extracting(BenchmarkPl3Option::code).containsExactly("PL3-A", "PL3-B");
        assertThat(options).extracting(BenchmarkPl3Option::name).containsExactly("Alpha", "Zebra");
    }

    private static BenchmarkingQuery query(
            String center,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            LocalDate submittedFrom,
            LocalDate submittedTo) {
        return new BenchmarkingQuery(center, domain, pl1, pl2, pl3Code, submittedFrom, submittedTo);
    }

    private static BenchmarkRow row(
            String pl3Code,
            String gbs,
            String domain,
            String pl1,
            String pl2,
            String submittedDate) {
        return new BenchmarkRow(
                gbs,
                "China",
                domain,
                pl1,
                pl2,
                pl2,
                pl3Code,
                new BigDecimal("120"),
                new BigDecimal("180"),
                new BigDecimal("13.0"),
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                submittedDate);
    }
}
