package com.cmacgm.gbs.rst.api.governance.application;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationWorkflowQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationWorkflowRow;

/**
 * Server-side matching and distinct option helpers for Validation Workflow rows.
 */
public final class ValidationWorkflowFilters {

    private ValidationWorkflowFilters() {
    }

    /**
     * Returns whether the row matches the query. Blank / null filters are ignored.
     *
     * @param row built validation row
     * @param query list filters
     * @return true when the row should be included
     */
    public static boolean matches(ValidationWorkflowRow row, ValidationWorkflowQuery query) {
        if (query == null) {
            return true;
        }
        if (hasText(query.exerciseCode())) {
            String code = row.exerciseNo() == null ? "" : row.exerciseNo();
            if (!code.toLowerCase(Locale.ROOT)
                    .contains(query.exerciseCode().trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (hasText(query.center()) && !query.center().equals(row.gbs())) {
            return false;
        }
        if (hasText(query.domain()) && !query.domain().equals(row.domain())) {
            return false;
        }
        if (hasText(query.pl3Name()) && !query.pl3Name().equals(row.pl3())) {
            return false;
        }
        if (hasText(query.toolkitName()) && !query.toolkitName().equals(row.toolkit())) {
            return false;
        }
        if (query.agingMinDays() != null
                && (row.agingDays() == null || row.agingDays() < query.agingMinDays())) {
            return false;
        }
        LocalDate submitted = dateOf(row.submittedDate());
        if (query.submittedFrom() != null
                && (submitted == null || submitted.isBefore(query.submittedFrom()))) {
            return false;
        }
        if (query.submittedTo() != null
                && (submitted == null || submitted.isAfter(query.submittedTo()))) {
            return false;
        }
        return true;
    }

    /**
     * Distinct non-blank values, sorted, for dropdown options.
     *
     * @param rows source rows (unfiltered)
     * @param getter field accessor
     * @return sorted distinct names
     */
    public static List<String> distinct(
            List<ValidationWorkflowRow> rows, Function<ValidationWorkflowRow, String> getter) {
        return rows.stream()
                .map(getter)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static LocalDate dateOf(String submittedDate) {
        if (submittedDate == null || submittedDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(submittedDate);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
