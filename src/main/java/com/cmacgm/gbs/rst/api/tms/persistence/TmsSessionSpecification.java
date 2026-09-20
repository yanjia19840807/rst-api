package com.cmacgm.gbs.rst.api.tms.persistence;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.time.CenterZones;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
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
                dateTo,
                null,
                null));
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
            Join<TmsSession, Toolkit> toolkit = null;
            if (filter.agentCcgid() != null) {
                predicates.add(builder.equal(root.get("agentCcgid"), filter.agentCcgid()));
            }
            if (filter.toolkitIds() != null) {
                if (filter.toolkitIds().isEmpty()) {
                    predicates.add(builder.disjunction());
                } else {
                    toolkit = toolkitJoin(root, toolkit);
                    predicates.add(toolkit.get("id").in(filter.toolkitIds()));
                }
            }
            if (filter.toolkitId() != null) {
                toolkit = toolkitJoin(root, toolkit);
                predicates.add(builder.equal(toolkit.get("id"), filter.toolkitId()));
            }
            if (hasText(filter.pl3Code())) {
                toolkit = toolkitJoin(root, toolkit);
                predicates.add(builder.equal(toolkit.get("primaryPl3Code"), filter.pl3Code().trim()));
            }
            if (hasText(filter.center())) {
                toolkit = toolkitJoin(root, toolkit);
                predicates.add(builder.equal(toolkit.get("center"), filter.center().trim()));
            }
            if (hasText(filter.domain())) {
                toolkit = toolkitJoin(root, toolkit);
                predicates.add(builder.equal(toolkit.get("domain"), filter.domain().trim()));
            }
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            if (filter.enabled() != null) {
                predicates.add(builder.equal(root.get("enabled"), filter.enabled()));
            }
            if (hasText(filter.sessionNo())) {
                String pattern = "%" + filter.sessionNo().trim().toLowerCase() + "%";
                predicates.add(builder.like(builder.lower(root.get("sessionNo")), pattern));
            }
            if (hasText(filter.reference())) {
                String pattern = "%" + filter.reference().trim().toLowerCase() + "%";
                predicates.add(builder.like(builder.lower(root.get("reference")), pattern));
            }
            if (hasText(filter.queryText())) {
                String pattern = "%" + filter.queryText().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("sessionNo")), pattern),
                        builder.like(builder.lower(root.get("reference")), pattern)));
            }
            if (filter.dateFrom() != null) {
                ZoneId zone = dateZone(filter);
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("startedAt"),
                        filter.dateFrom().atStartOfDay(zone).toInstant()));
            }
            if (filter.dateTo() != null) {
                ZoneId zone = dateZone(filter);
                predicates.add(builder.lessThan(
                        root.get("startedAt"),
                        filter.dateTo().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Session list filter criteria.
     *
     * @param agentCcgid when set, restrict to this agent (Agent list or Supervisor agent filter)
     * @param toolkitIds when set, restrict to these toolkits (Supervisor org scope or resolved filters)
     * @param toolkitId optional single toolkit filter within scope
     * @param pl3Code optional exact PL3 code
     * @param status optional status
     * @param sessionNo optional session number contains
     * @param reference optional reference contains
     * @param queryText optional sessionNo∪reference contains
     * @param dateFrom optional started-at lower bound (inclusive)
     * @param dateTo optional started-at upper bound (inclusive day)
     * @param enabled when set, restrict to enabled or disabled completed samples
     * @param dateCenter Center whose IANA zone interprets dateFrom / dateTo
     * @param center optional exact GBS Center on the live Toolkit
     * @param domain optional exact Domain on the live Toolkit
     * @param carrier unused by JPA; resolved to toolkitIds before query
     * @param site unused by JPA; resolved to toolkitIds before query
     * @param customerCountry unused by JPA; resolved to toolkitIds before query
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
            LocalDate dateTo,
            Boolean enabled,
            String dateCenter,
            String center,
            String domain,
            String carrier,
            String site,
            String customerCountry) {
        public Filter(
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
            this(
                    agentCcgid,
                    toolkitIds,
                    toolkitId,
                    pl3Code,
                    status,
                    sessionNo,
                    reference,
                    queryText,
                    dateFrom,
                    dateTo,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        public Filter(
                String agentCcgid,
                Collection<UUID> toolkitIds,
                UUID toolkitId,
                String pl3Code,
                TmsSessionStatus status,
                String sessionNo,
                String reference,
                String queryText,
                LocalDate dateFrom,
                LocalDate dateTo,
                Boolean enabled) {
            this(
                    agentCcgid,
                    toolkitIds,
                    toolkitId,
                    pl3Code,
                    status,
                    sessionNo,
                    reference,
                    queryText,
                    dateFrom,
                    dateTo,
                    enabled,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        public Filter(
                String agentCcgid,
                Collection<UUID> toolkitIds,
                UUID toolkitId,
                String pl3Code,
                TmsSessionStatus status,
                String sessionNo,
                String reference,
                String queryText,
                LocalDate dateFrom,
                LocalDate dateTo,
                Boolean enabled,
                String dateCenter) {
            this(
                    agentCcgid,
                    toolkitIds,
                    toolkitId,
                    pl3Code,
                    status,
                    sessionNo,
                    reference,
                    queryText,
                    dateFrom,
                    dateTo,
                    enabled,
                    dateCenter,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
    }

    private static Join<TmsSession, Toolkit> toolkitJoin(
            jakarta.persistence.criteria.Root<TmsSession> root, Join<TmsSession, Toolkit> existing) {
        return existing != null ? existing : root.join("toolkit", JoinType.INNER);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ZoneId dateZone(Filter filter) {
        return CenterZones.of(filter.dateCenter());
    }
}
