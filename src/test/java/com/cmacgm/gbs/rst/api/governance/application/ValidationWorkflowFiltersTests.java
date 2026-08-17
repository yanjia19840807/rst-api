package com.cmacgm.gbs.rst.api.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationWorkflowQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationWorkflowRow;
import org.junit.jupiter.api.Test;

class ValidationWorkflowFiltersTests {

    @Test
    void matchesAllWhenQueryIsEmpty() {
        assertThat(ValidationWorkflowFilters.matches(
                row("EX-1", "GBS China", "OPS", "PL3-A", "TK-1", 9, "2026-03-01"),
                query(null, null, null, null, null, null, null, null))).isTrue();
    }

    @Test
    void exerciseCodeIsCaseInsensitiveContains() {
        ValidationWorkflowRow row = row("EX-ABC-01", "GBS China", "OPS", "PL3-A", "TK-1", 9, "2026-03-01");
        assertThat(ValidationWorkflowFilters.matches(
                row, query("abc", null, null, null, null, null, null, null))).isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query("zzz", null, null, null, null, null, null, null))).isFalse();
    }

    @Test
    void exactFiltersMustMatch() {
        ValidationWorkflowRow row = row("EX-1", "GBS China", "OPS", "PL3-A", "TK-1", 9, "2026-03-01");
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, "GBS China", "OPS", "PL3-A", "TK-1", null, null, null))).isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, "GBS India", null, null, null, null, null, null))).isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, "FIN", null, null, null, null, null))).isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, "PL3-B", null, null, null, null))).isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, "TK-2", null, null, null))).isFalse();
    }

    @Test
    void agingIsAtLeastMinDays() {
        ValidationWorkflowRow row = row("EX-1", "GBS China", "OPS", "PL3-A", "TK-1", 14, "2026-03-01");
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, 14, null, null))).isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, 15, null, null))).isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row("EX-2", "GBS China", "OPS", "PL3-A", "TK-1", null, "2026-03-01"),
                query(null, null, null, null, null, 0, null, null))).isFalse();
    }

    @Test
    void submittedDateIsInclusive() {
        ValidationWorkflowRow row = row("EX-1", "GBS China", "OPS", "PL3-A", "TK-1", 9, "2026-03-10");
        assertThat(ValidationWorkflowFilters.matches(
                row,
                query(null, null, null, null, null, null,
                        LocalDate.parse("2026-03-10"), LocalDate.parse("2026-03-10"))))
                .isTrue();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, LocalDate.parse("2026-03-11"), null)))
                .isFalse();
        assertThat(ValidationWorkflowFilters.matches(
                row, query(null, null, null, null, null, null, null, LocalDate.parse("2026-03-09"))))
                .isFalse();
    }

    @Test
    void distinctIgnoresBlankAndSorts() {
        List<String> names = ValidationWorkflowFilters.distinct(
                List.of(
                        row("EX-1", "GBS India", "OPS", "B", "TK", 1, "2026-01-01"),
                        row("EX-2", "GBS China", "OPS", "A", "TK", 1, "2026-01-01"),
                        row("EX-3", "", "OPS", "A", "TK", 1, "2026-01-01"),
                        row("EX-4", "GBS China", "OPS", "A", "TK", 1, "2026-01-01")),
                ValidationWorkflowRow::gbs);
        assertThat(names).containsExactly("GBS China", "GBS India");
    }

    private static ValidationWorkflowQuery query(
            String exerciseCode,
            String center,
            String domain,
            String pl3Name,
            String toolkitName,
            Integer agingMinDays,
            LocalDate submittedFrom,
            LocalDate submittedTo) {
        return new ValidationWorkflowQuery(
                exerciseCode,
                center,
                domain,
                pl3Name,
                toolkitName,
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
                gbs,
                domain,
                pl3,
                toolkit,
                "Manager Review",
                "Ada",
                agingDays,
                BigDecimal.ONE,
                BigDecimal.TEN,
                "",
                submittedDate);
    }
}
