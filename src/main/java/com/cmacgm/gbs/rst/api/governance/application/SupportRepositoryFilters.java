package com.cmacgm.gbs.rst.api.governance.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.cmacgm.gbs.rst.api.common.time.CenterDates;
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
        if (hasText(query.exerciseCode())) {
            String code = row.exerciseNo() == null ? "" : row.exerciseNo();
            if (!code.toLowerCase(Locale.ROOT)
                    .contains(query.exerciseCode().trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (hasText(query.center()) && !query.center().equals(row.center())) {
            return false;
        }
        if (hasText(query.domain()) && !query.domain().equals(row.domain())) {
            return false;
        }
        if (hasText(query.pl3Name()) && !query.pl3Name().equals(row.pl3())) {
            return false;
        }
        if (query.categoryId() != null && !query.categoryId().equals(row.categoryId())) {
            return false;
        }
        if (hasText(query.toolkitName()) && !query.toolkitName().equals(row.toolkit())) {
            return false;
        }
        if (hasText(query.carrier()) && !containsValue(row.carriers(), query.carrier())) {
            return false;
        }
        if (hasText(query.site()) && !containsValue(row.sites(), query.site())) {
            return false;
        }
        if (hasText(query.customerCountry()) && !containsValue(row.customerCountries(), query.customerCountry())) {
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
    public static List<String> distinct(
            List<SupportRepositoryRow> rows, Function<SupportRepositoryRow, String> getter) {
        return rows.stream()
                .map(getter)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Distinct values from Exercise-level 1:N scope lists, for dropdowns.
     *
     * @param rows source rows (unfiltered)
     * @param getter list accessor
     * @return sorted distinct names
     */
    public static List<String> distinctValues(
            List<SupportRepositoryRow> rows, Function<SupportRepositoryRow, List<String>> getter) {
        return rows.stream()
                .map(getter)
                .filter(values -> values != null && !values.isEmpty())
                .flatMap(List::stream)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Splits each value on commas, trims, and keeps first-seen literals.
     *
     * @param values raw country names
     * @return distinct tokens in first-seen order
     */
    public static List<String> distinctCommaTokens(List<String> values) {
        return CommaTokens.distinct(values);
    }

    private static boolean containsValue(List<String> values, String selected) {
        return values != null && values.contains(selected);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
