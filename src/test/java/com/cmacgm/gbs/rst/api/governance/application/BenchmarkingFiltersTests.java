package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkProcessPath;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkingQuery;
import org.junit.jupiter.api.Test;

class BenchmarkingFiltersTests {

    @Test
    void requiresPl3Code() {
        BenchmarkRow row = row("PL3-BANK", "FINANCE", "R2R", "Bank Rec", "2026-03-10");
        assertThat(BenchmarkingFilters.matches(row, query(null, null, null, null, null, null, null, null, null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(row, query(null, null, null, null, null, "PL3-BANK", null, null, null, null, null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(row, query(null, null, null, null, null, "PL3-OTHER", null, null, null, null, null, null)))
                .isFalse();
    }

    @Test
    void exactFiltersMustMatch() {
        BenchmarkRow row = row("PL3-BANK", "FINANCE", "R2R", "Bank Rec", "2026-03-10");
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, "FINANCE", "R2R", "Bank Rec", "PL3-BANK", null, null, null, null, null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, "OPS", null, null, "PL3-BANK", null, null, null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, "P2P", null, "PL3-BANK", null, null, null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, "AP", "PL3-BANK", null, null, null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, "GBS INDIA", null, null, null, "PL3-BANK", null, null, null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, null, "PL3-BANK", "MSC", null, null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, null, "PL3-BANK", null, "OTHER", null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, null, "PL3-BANK", null, null, "FRANCE", null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, null, "PL3-BANK", null, null, null, "2026-05", null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, null, "PL3-BANK", null, null, null, "2026-06", null, null)))
                .isTrue();
    }

    @Test
    void customerCountryFilterMatchesCommaTokens() {
        BenchmarkRow row = new BenchmarkRow(
                "EX-1",
                "GBS LEBANON",
                "CMA CGM",
                "SHA",
                "HONG KONG SAR, CHINA",
                "FINANCE",
                "R2R",
                "Bank Rec",
                "Bank Rec",
                "PL3-BANK",
                new BigDecimal("120"),
                new BigDecimal("180"),
                new BigDecimal("13.0"),
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                "2026-06",
                "2026-03-10");
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, null, "PL3-BANK", null, null, "CHINA", null, null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, null, "PL3-BANK", null, null, "HONG KONG SAR", null, null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, null, "PL3-BANK", null, null, "FRANCE", null, null, null)))
                .isFalse();
    }

    @Test
    void exerciseCodeIsCaseInsensitiveContains() {
        BenchmarkRow row = row("PL3-BANK", "FINANCE", "R2R", "Bank Rec", "2026-03-10");
        assertThat(BenchmarkingFilters.matches(
                row, query("ex-1", null, null, null, null, "PL3-BANK", null, null, null, null, null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(
                row, query("EX-9", null, null, null, null, "PL3-BANK", null, null, null, null, null, null)))
                .isFalse();
    }

    @Test
    void validatedDateIsInclusive() {
        BenchmarkRow row = row("PL3-BANK", "FINANCE", "R2R", "Bank Rec", "2026-03-10");
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, null, "PL3-BANK", null, null, null, null,
                        LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, null, "PL3-BANK", null, null, null, null,
                        LocalDate.parse("2026-03-11"), null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matches(
                row, query(null, null, null, null, null, "PL3-BANK", null, null, null, null,
                        null, LocalDate.parse("2026-03-09"))))
                .isFalse();
        BenchmarkRow timed = row("PL3-BANK", "FINANCE", "R2R", "Bank Rec", "2026-03-10T16:45:00");
        assertThat(BenchmarkingFilters.matches(
                timed, query(null, null, null, null, null, "PL3-BANK", null, null, null, null,
                        LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
    }

    @Test
    void matchesExerciseNeedsPl3LineAndHonorsMonthAndDate() {
        RstExercise june = exercise(
                LocalDate.of(2026, 6, 1), Instant.parse("2026-06-10T02:00:00Z"), "PL3-BANK");
        RstExercise september = exercise(
                LocalDate.of(2026, 9, 1), Instant.parse("2026-09-10T02:00:00Z"), "PL3-BANK");

        assertThat(BenchmarkingFilters.matchesExercise(
                june, query(null, null, "FINANCE", "R2R", "Bank Rec", "PL3-BANK", null, null, null, null, null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matchesExercise(
                september, query(null, null, null, null, null, "PL3-BANK", null, null, null, "2026-06", null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matchesExercise(
                june, query(null, null, null, null, null, "PL3-BANK", null, null, null, "2026-06", null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matchesExercise(
                september, query(null, null, null, null, null, "PL3-BANK", null, null, null, null, null,
                        LocalDate.parse("2026-06-30"))))
                .isFalse();
        assertThat(BenchmarkingFilters.matchesExercise(
                june, query(null, null, null, null, null, "PL3-BANK", null, null, null, null, null,
                        LocalDate.parse("2026-06-30"))))
                .isTrue();
        assertThat(BenchmarkingFilters.matchesExercise(
                june, query(null, null, null, null, null, "PL3-OTHER", null, null, null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matchesExercise(
                june, query(null, null, null, null, null, null, null, null, null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matchesExercise(
                june, query("EX-PL3", null, null, null, null, "PL3-BANK", null, null, null, null, null, null)))
                .isTrue();
        assertThat(BenchmarkingFilters.matchesExercise(
                june, query(null, "GBS INDIA", null, null, null, "PL3-BANK", null, null, null, null, null, null)))
                .isFalse();
        assertThat(BenchmarkingFilters.matchesExercise(
                june, query(null, null, null, null, null, "PL3-BANK", null, null, "China", null, null, null)))
                .isTrue();
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
            String exerciseCode,
            String center,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String carrier,
            String site,
            String customerCountry,
            String sizingMonth,
            LocalDate validatedFrom,
            LocalDate validatedTo) {
        return new BenchmarkingQuery(
                exerciseCode,
                center,
                domain,
                pl1,
                pl2,
                pl3Code,
                carrier,
                site,
                customerCountry,
                sizingMonth,
                validatedFrom,
                validatedTo);
    }

    private static BenchmarkRow row(
            String pl3Code,
            String domain,
            String pl1,
            String pl2,
            String validatedDate) {
        return new BenchmarkRow(
                "EX-1",
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
                "2026-06",
                validatedDate);
    }

    private static RstExercise exercise(LocalDate sizingMonth, Instant validatedAt, String pl3Code) {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        RstExercise exercise = RstExercise.create(
                UUID.randomUUID(),
                "EX-" + pl3Code,
                UUID.randomUUID(),
                "SUP1",
                sizingMonth,
                null,
                null,
                null,
                null,
                now);
        exercise.freezeToolkitSnapshot(
                exercise.getToolkitId(),
                1L,
                UUID.randomUUID(),
                "Toolkit",
                "175344",
                "GBS CHINA",
                "FINANCE",
                "R2R",
                "Bank Rec",
                pl3Code,
                "BANK RECONCILIATION",
                false,
                "SUP1",
                now);
        exercise.addSharedKpiLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "GBS CHINA",
                "SHA",
                "FINANCE",
                "R2R",
                "Bank Rec",
                pl3Code,
                "BANK RECONCILIATION",
                "CMA CGM",
                "China",
                BigDecimal.ONE,
                "SUP1",
                now);
        if (validatedAt != null) {
            exercise.markApproved("LTH1", validatedAt);
        }
        return exercise;
    }
}
