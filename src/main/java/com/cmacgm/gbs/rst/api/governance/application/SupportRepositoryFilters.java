package com.cmacgm.gbs.rst.api.governance.application;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Function;

import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryRow;

/**
 * Server-side matching and distinct option helpers for Support Repository rows.
 */
public final class SupportRepositoryFilters {

    private SupportRepositoryFilters() {
    }

    /**
     * Returns whether the row matches the query. Blank / null filters are ignored.
     *
     * @param row built support row
     * @param query list filters
     * @return true when the row should be included
     */
    public static boolean matches(SupportRepositoryRow row, SupportRepositoryQuery query) {
        if (query == null) {
            return true;
        }
        if (hasText(query.center()) && !query.center().equals(row.center())) {
            return false;
        }
        if (query.categoryId() != null && !query.categoryId().equals(row.categoryId())) {
            return false;
        }
        if (hasText(query.toolkitName()) && !query.toolkitName().equals(row.toolkit())) {
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
            List<SupportRepositoryRow> rows, Function<SupportRepositoryRow, String> getter) {
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
