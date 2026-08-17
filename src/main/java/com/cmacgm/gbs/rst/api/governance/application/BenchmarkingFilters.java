package com.cmacgm.gbs.rst.api.governance.application;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkPl3Option;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkingQuery;

/**
 * Server-side matching and distinct option helpers for benchmarking rows.
 */
public final class BenchmarkingFilters {

    private BenchmarkingFilters() {
    }

    /**
     * Returns whether the row matches the query. Blank / null filters are ignored.
     * {@code pl3Code} is required for a row to match; blank code yields no matches.
     *
     * @param row built benchmark row
     * @param query list filters
     * @return true when the row should be included
     */
    public static boolean matches(BenchmarkRow row, BenchmarkingQuery query) {
        if (query == null || !hasText(query.pl3Code())) {
            return false;
        }
        if (!query.pl3Code().equals(row.pl3Code())) {
            return false;
        }
        if (hasText(query.center()) && !query.center().equals(row.gbs())) {
            return false;
        }
        if (hasText(query.domain()) && !query.domain().equals(row.domain())) {
            return false;
        }
        if (hasText(query.pl1()) && !query.pl1().equals(row.pl1())) {
            return false;
        }
        if (hasText(query.pl2()) && !query.pl2().equals(row.pl2())) {
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
    public static List<String> distinct(List<BenchmarkRow> rows, Function<BenchmarkRow, String> getter) {
        return rows.stream()
                .map(getter)
                .filter(BenchmarkingFilters::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Distinct PL3 codes with a display name, sorted by name then code.
     *
     * @param rows source rows (unfiltered)
     * @return PL3 options
     */
    public static List<BenchmarkPl3Option> distinctPl3(List<BenchmarkRow> rows) {
        Map<String, String> names = new LinkedHashMap<>();
        for (BenchmarkRow row : rows) {
            if (!hasText(row.pl3Code())) {
                continue;
            }
            names.putIfAbsent(row.pl3Code(), hasText(row.pl3()) ? row.pl3() : row.pl3Code());
        }
        List<BenchmarkPl3Option> options = new ArrayList<>();
        names.forEach((code, name) -> options.add(new BenchmarkPl3Option(code, name)));
        options.sort(Comparator
                .comparing(BenchmarkPl3Option::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BenchmarkPl3Option::code));
        return List.copyOf(options);
    }

    static boolean hasText(String value) {
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
