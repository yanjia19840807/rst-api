package com.cmacgm.gbs.rst.api.governance.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.cmacgm.gbs.rst.api.common.time.CenterDates;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryListQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryRow;

/**
 * Server-side matching and distinct option helpers for RST Repository rows.
 */
public final class RepositoryRowFilters {

    private RepositoryRowFilters() {
    }

    /**
     * Returns whether the row matches the query. Blank / null filters are ignored.
     *
     * @param row built repository row
     * @param query list filters
     * @return true when the row should be included
     */
    public static boolean matches(RepositoryRow row, RepositoryListQuery query) {
        if (query == null) {
            return true;
        }
        if (hasText(query.exerciseCode())) {
            String code = row.exerciseId() == null ? "" : row.exerciseId();
            if (!code.toLowerCase(Locale.ROOT)
                    .contains(query.exerciseCode().trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (hasText(query.center()) && !query.center().equals(row.country())) {
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
        if (hasText(query.carrier()) && !query.carrier().equals(row.carrier())) {
            return false;
        }
        if (hasText(query.site()) && !query.site().equals(row.site())) {
            return false;
        }
        if (hasText(query.customerCountry()) && !CommaTokens.contains(row.kpi(), query.customerCountry())) {
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
     * Distinct non-blank values, sorted, for dropdown options.
     *
     * @param rows source rows (unfiltered)
     * @param getter field accessor
     * @return sorted distinct names
     */
    public static List<String> distinct(List<RepositoryRow> rows, Function<RepositoryRow, String> getter) {
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

}
