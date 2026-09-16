package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkProcessPath;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkingQuery;
import org.junit.jupiter.api.Test;

class BenchmarkingFiltersTests {

    @Test
    void requiresPl3Code() {
        BenchmarkRow row = row("PL3-BANK", "FINANCE", "R2R", "Bank Rec", "2026-03-10");
        assertThat(BenchmarkingFilters.matches(row, query(null, null, null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(row, query(null, null, null, "PL3-BANK", null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(row, query(null, null, null, "PL3-OTHER", null, null)))
                .isFalse();
    }

    @Test
    void exactFiltersMustMatch() {
        BenchmarkRow row = row("PL3-BANK", "FINANCE", "R2R", "Bank Rec", "2026-03-10");
        assertThat(BenchmarkingFilters.matches(
                row, query("FINANCE", "R2R", "Bank Rec", "PL3-BANK", null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(
                row, query("OPS", null, null, "PL3-BANK", null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, "P2P", null, "PL3-BANK", null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, "AP", "PL3-BANK", null, null)))
                .isFalse();
    }

    @Test
    void validatedDateIsInclusive() {
        BenchmarkRow row = row("PL3-BANK", "FINANCE", "R2R", "Bank Rec", "2026-03-10");
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, "PL3-BANK",
                        LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, "PL3-BANK", LocalDate.parse("2026-03-11"), null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, "PL3-BANK", null, LocalDate.parse("2026-03-09"))))
                .isFalse();
    }

    @Test
    void distinctPathsKeepsHierarchyAndSorts() {
        List<BenchmarkProcessPath> paths = BenchmarkingFilters.distinctPaths(List.of(
                row("PL3-B", "FINANCE", "R2R", "Zebra", "2026-01-01"),
                row("PL3-A", "FINANCE", "R2R", "Alpha", "2026-01-01"),
                row("PL3-A", "FINANCE", "R2R", "Alpha", "2026-01-01"),
                row("", "FINANCE", "R2R", "Missing", "2026-01-01")));
        assertThat(paths).extracting(BenchmarkProcessPath::pl3Code).containsExactly("PL3-A", "PL3-B");
        assertThat(paths).extracting(BenchmarkProcessPath::pl3Name).containsExactly("Alpha", "Zebra");
        assertThat(paths).extracting(BenchmarkProcessPath::domain).containsOnly("FINANCE");
    }

    private static BenchmarkingQuery query(
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            LocalDate validatedFrom,
            LocalDate validatedTo) {
        return new BenchmarkingQuery(domain, pl1, pl2, pl3Code, validatedFrom, validatedTo);
    }

    private static BenchmarkRow row(
            String pl3Code,
            String domain,
            String pl1,
            String pl2,
            String validatedDate) {
        return new BenchmarkRow(
                "GBS LEBANON",
                "CMA CGM",
                "SHA",
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
                validatedDate);
    }
}
