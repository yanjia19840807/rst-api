package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.time.LocalDate;
import java.util.ArrayList;

import jakarta.persistence.criteria.Predicate;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import org.springframework.data.jpa.domain.Specification;

/**
 * Recent-run list filters.
 */
public final class TimesheetSyncRunSpecification {

    private TimesheetSyncRunSpecification() {
    }

    /**
     * Filters by kind, status and sync-date range. Blank values are ignored.
     *
     * @param kind DAILY or MONTHLY
     * @param status run status
     * @param dateFrom inclusive sync date
     * @param dateTo inclusive sync date
     * @return specification
     */
    public static Specification<TimesheetSyncRun> filtered(
            String kind, String status, LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            String kindFilter = blankToNull(kind);
            if (kindFilter != null) {
                predicates.add(builder.equal(root.get("kind"), kindFilter.toUpperCase()));
            }
            String statusFilter = blankToNull(status);
            if (statusFilter != null) {
                predicates.add(builder.equal(root.get("status"), statusFilter.toUpperCase()));
            }
            if (dateFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("syncDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("syncDate"), dateTo));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
