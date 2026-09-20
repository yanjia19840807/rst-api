package com.cmacgm.gbs.rst.api.governance.application;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.cmacgm.gbs.rst.api.common.time.CenterDates;
import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationPersonOption;
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
        if (hasText(query.carrier()) && !containsValue(row.carriers(), query.carrier())) {
            return false;
        }
        if (hasText(query.site()) && !containsValue(row.sites(), query.site())) {
            return false;
        }
        if (hasText(query.customerCountry()) && !containsValue(row.customerCountries(), query.customerCountry())) {
            return false;
        }
        if (hasText(query.currentStep()) && !query.currentStep().equals(row.currentStep())) {
            return false;
        }
        if (hasText(query.currentOwner()) && !query.currentOwner().equals(row.currentOwnerCcgid())) {
            return false;
        }
        if (hasText(query.sizingMonth()) && !query.sizingMonth().trim().equals(row.sizingMonth())) {
            return false;
        }
        if (query.agingMinDays() != null
                && (row.agingDays() == null || row.agingDays() < query.agingMinDays())) {
            return false;
        }
        LocalDate submitted = CenterDates.civilDateOf(row.submittedDate());
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

    /**
     * Distinct values from Exercise-level 1:N scope lists, for dropdowns.
     *
     * @param rows source rows (unfiltered)
     * @param getter list accessor
     * @return sorted distinct names
     */
    public static List<String> distinctValues(
            List<ValidationWorkflowRow> rows, Function<ValidationWorkflowRow, List<String>> getter) {
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
     * Distinct current owners by CCGID, sorted by display name.
     *
     * @param rows source rows (unfiltered)
     * @return owner options
     */
    public static List<ValidationPersonOption> distinctOwners(List<ValidationWorkflowRow> rows) {
        Map<String, String> owners = new LinkedHashMap<>();
        for (ValidationWorkflowRow row : rows) {
            if (row.currentOwnerCcgid() == null || row.currentOwnerCcgid().isBlank()) {
                continue;
            }
            owners.putIfAbsent(
                    row.currentOwnerCcgid(),
                    row.currentOwner() == null ? "" : row.currentOwner());
        }
        return owners.entrySet().stream()
                .map(entry -> new ValidationPersonOption(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparing(ValidationPersonOption::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(ValidationPersonOption::ccgid, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private static boolean containsValue(List<String> values, String selected) {
        return values != null && values.contains(selected);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
