package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationPersonOption;
import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationWorkflowQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationWorkflowRow;
import org.junit.jupiter.api.Test;

class ValidationWorkflowFiltersTests {

    @Test
    void matchesAllWhenQueryIsEmpty() {
        assertThat(ValidationWorkflowFilters.matches(
                row("EX-1", "GBS LEBANON", "OPS", "PL3-A", "TK-1", 9, "2026-03-01"),
                query(null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
                .isTrue();
    }

    @Test
    void exerciseCodeIsCaseInsensitiveContains() {
        ValidationWorkflowRow row = row("EX-ABC-01", "GBS LEBANON", "OPS", "PL3-A", "TK-1", 9, "2026-03-01");
        assertThat(ValidationWorkflowFilters.matches(
                row, query("abc", null, null, null, null, null, null, null, null, null, null, null, null, null)))
                .isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query("zzz", null, null, null, null, null, null, null, null, null, null, null, null, null)))
                .isFalse();
    }

    @Test
    void exactFiltersMustMatch() {
        ValidationWorkflowRow row = row("EX-1", "GBS LEBANON", "OPS", "PL3-A", "TK-1", 9, "2026-03-01");
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, "GBS LEBANON", "OPS", "PL3-A", "TK-1", "CMA CGM", "CNCKG", "CHINA",
                        "Manager Review", "ADA001", "2026-03", null, null, null)))
                .isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, "GBS INDIA", null, null, null, null, null, null, null, null, null, null, null, null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, "FIN", null, null, null, null, null, null, null, null, null, null, null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, "PL3-B", null, null, null, null, null, null, null, null, null, null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, "TK-2", null, null, null, null, null, null, null, null, null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, "MSC", null, null, null, null, null, null, null, null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, "OTHER", null, null, null, null, null, null, null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, "FRANCE", null, null, null, null, null, null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, "CDH Review", null, null, null, null, null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, null, "BEN001", null, null, null, null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, null, null, "2026-05", null, null, null)))
                .isFalse();
    }

    @Test
    void kpiScopeMatchesAnyValueOnTheExercise() {
        ValidationWorkflowRow row = row("EX-1", "GBS LEBANON", "OPS", "PL3-A", "TK-1", 9, "2026-03-01");
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, "CMA CGM", null, null, null, null, null, null, null, null)))
                .isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, "CNCKG", null, null, null, null, null, null, null)))
                .isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, "HONG KONG SAR", null, null, null, null, null, null)))
                .isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, "CHINA", null, null, null, null, null, null)))
                .isTrue();
    }

    @Test
    void agingIsAtLeastMinDays() {
        ValidationWorkflowRow row = row("EX-1", "GBS LEBANON", "OPS", "PL3-A", "TK-1", 14, "2026-03-01");
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, null, null, null, 14, null, null)))
                .isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, null, null, null, 15, null, null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row("EX-2", "GBS LEBANON", "OPS", "PL3-A", "TK-1", null, "2026-03-01"),
                query(null, null, null, null, null, null, null, null, null, null, null, 0, null, null)))
                .isFalse();
    }

    @Test
    void submittedDateIsInclusive() {
        ValidationWorkflowRow row = row("EX-1", "GBS LEBANON", "OPS", "PL3-A", "TK-1", 9, "2026-03-10");
        assertThat(ValidationWorkflowFilters.matches(
                row,
                query(null, null, null, null, null, null, null, null, null, null, null, null,
                        LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, null, null, null, null,
                        LocalDate.parse("2026-03-11"), null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, null, null, null, null,
                        null, LocalDate.parse("2026-03-09"))))
                .isFalse();
        ValidationWorkflowRow timed = row("EX-1", "GBS LEBANON", "OPS", "PL3-A", "TK-1", 9, "2026-03-10T16:45:00");
        assertThat(ValidationWorkflowFilters.matches(
                timed,
                query(null, null, null, null, null, null, null, null, null, null, null, null,
                        LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
    }

    @Test
    void distinctIgnoresBlankAndSorts() {
        List<String> names = ValidationWorkflowFilters.distinct(
                List.of(
                        row("EX-1", "GBS INDIA", "OPS", "B", "TK", 1, "2026-01-01"),
                        row("EX-2", "GBS LEBANON", "OPS", "A", "TK", 1, "2026-01-01"),
                        row("EX-3", "", "OPS", "A", "TK", 1, "2026-01-01"),
                        row("EX-4", "GBS LEBANON", "OPS", "A", "TK", 1, "2026-01-01")),
                ValidationWorkflowRow::gbs);
        assertThat(names).containsExactly("GBS INDIA", "GBS LEBANON");
    }

    @Test
    void currentOwnerFilterMatchesCcgid() {
        ValidationWorkflowRow row = row("EX-1", "GBS LEBANON", "OPS", "PL3-A", "TK-1", 9, "2026-03-01");
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, null, "ADA001", null, null, null, null)))
                .isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, null, null, "Ada", null, null, null, null)))
                .isFalse();
    }

    @Test
    void distinctOwnersDedupesByCcgid() {
        assertThat(ValidationWorkflowFilters.distinctOwners(
                List.of(
                        row("EX-1", "GBS LEBANON", "OPS", "A", "TK", 1, "2026-01-01"),
                        row("EX-2", "GBS LEBANON", "OPS", "A", "TK", 1, "2026-01-01"))))
                .extracting(ValidationPersonOption::ccgid)
                .containsExactly("ADA001");
    }

    @Test
    void distinctValuesFlattensScopeLists() {
        List<String> countries = ValidationWorkflowFilters.distinctValues(
                List.of(row("EX-1", "GBS LEBANON", "OPS", "A", "TK", 1, "2026-01-01")),
                ValidationWorkflowRow::customerCountries);
        assertThat(countries).containsExactly("CHINA", "HONG KONG SAR");
    }

    private static ValidationWorkflowQuery query(
            String exerciseCode,
            String center,
            String domain,
            String pl3Name,
            String toolkitName,
            String carrier,
            String site,
            String customerCountry,
            String currentStep,
            String currentOwner,
            String sizingMonth,
            Integer agingMinDays,
            LocalDate submittedFrom,
            LocalDate submittedTo) {
        return new ValidationWorkflowQuery(
                exerciseCode,
                center,
                domain,
                pl3Name,
                toolkitName,
                carrier,
                site,
                customerCountry,
                currentStep,
                currentOwner,
                sizingMonth,
                agingMinDays,
                submittedFrom,
                submittedTo);
    }

    private static ValidationWorkflowRow row(
            String exerciseNo,
            String gbs,
            String domain,
            String pl3,
            String toolkit,
            Integer agingDays,
            String submittedDate) {
        return new ValidationWorkflowRow(
                exerciseNo,
                "11111111-1111-1111-1111-111111111111",
                gbs,
                domain,
                "PL1-A",
                "PL2-A",
                pl3,
                toolkit,
                List.of("CMA CGM"),
                List.of("CNCKG"),
                List.of("CHINA", "HONG KONG SAR"),
                "Manager Review",
                "Ada",
                "ADA001",
                agingDays,
                BigDecimal.ONE,
                BigDecimal.TEN,
                "",
                "2026-03",
                submittedDate);
    }
}
