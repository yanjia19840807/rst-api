package com.cmacgm.gbs.rst.api.tms.persistence;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import org.springframework.data.jpa.domain.Specification;

public final class TmsSessionSpecification {

    private TmsSessionSpecification() {
    }

    /**
     * Agent-owned session list filter (single agent ccgid).
     */
    public static Specification<TmsSession> filtered(
            String agentCcgid,
            TmsSessionStatus status,
            String sessionNo,
            String reference,
            String queryText,
            LocalDate dateFrom,
            LocalDate dateTo) {
        return filtered(new Filter(
                agentCcgid,
                null,
                null,
                null,
                status,
                sessionNo,
                reference,
                queryText,
                dateFrom,
                dateTo));
    }

    /**
     * Flexible session list filter for Agent or Supervisor scopes.
     *
     * @param filter query criteria
     * @return JPA specification
     */
    public static Specification<TmsSession> filtered(Filter filter) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (filter.agentCcgid() != null) {
                predicates.add(builder.equal(root.get("agentCcgid"), filter.agentCcgid()));
            }
            if (filter.toolkitIds() != null) {
                if (filter.toolkitIds().isEmpty()) {
                    predicates.add(builder.disjunction());
                } else {
                    predicates.add(root.get("toolkit").get("id").in(filter.toolkitIds()));
                }
            }
            if (filter.toolkitId() != null) {
                predicates.add(builder.equal(root.get("toolkit").get("id"), filter.toolkitId()));
            }
            if (filter.pl3Code() != null && !filter.pl3Code().isBlank()) {
                predicates.add(builder.equal(
                        root.get("toolkit").get("primaryPl3Code"), filter.pl3Code().trim()));
            }
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            if (filter.sessionNo() != null && !filter.sessionNo().isBlank()) {
                String pattern = "%" + filter.sessionNo().trim().toLowerCase() + "%";
                predicates.add(builder.like(builder.lower(root.get("sessionNo")), pattern));
            }
            if (filter.reference() != null && !filter.reference().isBlank()) {
                String pattern = "%" + filter.reference().trim().toLowerCase() + "%";
                predicates.add(builder.like(builder.lower(root.get("reference")), pattern));
            }
            if (filter.queryText() != null && !filter.queryText().isBlank()) {
                String pattern = "%" + filter.queryText().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("sessionNo")), pattern),
                        builder.like(builder.lower(root.get("reference")), pattern)));
            }
            if (filter.dateFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("startedAt"),
                        filter.dateFrom().atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (filter.dateTo() != null) {
                predicates.add(builder.lessThan(
                        root.get("startedAt"),
                        filter.dateTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Session list filter criteria.
     *
     * @param agentCcgid when set, restrict to this agent (Agent list or Supervisor agent filter)
     * @param toolkitIds when set, restrict to these toolkits (Supervisor org scope)
     * @param toolkitId optional single toolkit filter within scope
     * @param pl3Code optional PL3 code filter (current Toolkit PL3)
     * @param status optional status
     * @param sessionNo optional session number contains
     * @param reference optional reference contains
     * @param queryText optional sessionNo∪reference contains
     * @param dateFrom optional started-at lower bound (inclusive)
     * @param dateTo optional started-at upper bound (inclusive day)
     */
    public record Filter(
            String agentCcgid,
            Collection<UUID> toolkitIds,
            UUID toolkitId,
            String pl3Code,
            TmsSessionStatus status,
            String sessionNo,
            String reference,
            String queryText,
            LocalDate dateFrom,
            LocalDate dateTo) {
    }
}
