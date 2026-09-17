package com.cmacgm.gbs.rst.api.governance.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cmacgm.gbs.rst.api.common.time.CenterDates;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkProcessPath;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkRow;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkingQuery;

/**
 * Server-side matching and cascade-path helpers for benchmarking rows.
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
        if (hasText(query.domain()) && !query.domain().equals(row.domain())) {
            return false;
        }
        if (hasText(query.pl1()) && !query.pl1().equals(row.pl1())) {
            return false;
        }
        if (hasText(query.pl2()) && !query.pl2().equals(row.pl2())) {
            return false;
        }
        if (hasText(query.sizingMonth()) && !query.sizingMonth().trim().equals(row.sizingMonth())) {
            return false;
        }
        LocalDate validated = CenterDates.civilDateOf(row.validatedDate());
        if (query.validatedFrom() != null
                && (validated == null || validated.isBefore(query.validatedFrom()))) {
            return false;
        }
        if (query.validatedTo() != null
                && (validated == null || validated.isAfter(query.validatedTo()))) {
            return false;
        }
        return true;
    }

    /**
     * Distinct Domain → PL1 → PL2 → PL3 paths from APPROVED rows, for cascade pickers.
     *
     * @param rows source rows (unfiltered)
     * @return sorted distinct paths
     */
    public static List<BenchmarkProcessPath> distinctPaths(List<BenchmarkRow> rows) {
        Map<String, BenchmarkProcessPath> seen = new LinkedHashMap<>();
        for (BenchmarkRow row : rows) {
            if (!hasText(row.domain())
                    || !hasText(row.pl1())
                    || !hasText(row.pl2())
                    || !hasText(row.pl3Code())) {
                continue;
            }
            String key = row.domain() + '\0' + row.pl1() + '\0' + row.pl2() + '\0' + row.pl3Code();
            String name = hasText(row.pl3()) ? row.pl3() : row.pl3Code();
            seen.putIfAbsent(key, new BenchmarkProcessPath(
                    row.domain(), row.pl1(), row.pl2(), row.pl3Code().trim(), name));
        }
        List<BenchmarkProcessPath> paths = new ArrayList<>(seen.values());
        paths.sort(Comparator
                .comparing(BenchmarkProcessPath::domain, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BenchmarkProcessPath::pl1, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BenchmarkProcessPath::pl2, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BenchmarkProcessPath::pl3Name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(BenchmarkProcessPath::pl3Code));
        return List.copyOf(paths);
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
