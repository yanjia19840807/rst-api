package com.cmacgm.gbs.rst.api.tms.persistence;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import org.springframework.data.jpa.domain.Specification;

public final class TmsSessionSpecification {

    private TmsSessionSpecification() {
    }

    public static Specification<TmsSession> filtered(
            UUID userId,
            TmsSessionStatus status,
            String sessionNo,
            String reference,
            String queryText,
            LocalDate dateFrom,
            LocalDate dateTo) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("user").get("id"), userId));
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (sessionNo != null && !sessionNo.isBlank()) {
                String pattern = "%" + sessionNo.trim().toLowerCase() + "%";
                predicates.add(builder.like(builder.lower(root.get("sessionNo")), pattern));
            }
            if (reference != null && !reference.isBlank()) {
                String pattern = "%" + reference.trim().toLowerCase() + "%";
                predicates.add(builder.like(builder.lower(root.get("reference")), pattern));
            }
            if (queryText != null && !queryText.isBlank()) {
                String pattern = "%" + queryText.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("sessionNo")), pattern),
                        builder.like(builder.lower(root.get("reference")), pattern)));
            }
            if (dateFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("startedAt"),
                        dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (dateTo != null) {
                predicates.add(builder.lessThan(
                        root.get("startedAt"),
                        dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
