package com.cmacgm.gbs.rst.api.timesheet.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetAssignment;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetAssignmentRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetKpiRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetScopeRepository;

/**
 * Paged ACTIVE snapshot tables for the Timesheet Sync monitor.
 */
@Service
public class TimesheetSnapshotBrowseService {

    private final TimesheetPersonRepository people;
    private final TimesheetPositionRepository positions;
    private final TimesheetScopeRepository scopes;
    private final TimesheetAssignmentRepository assignments;
    private final TimesheetKpiRepository kpis;

    /**
     * @param people Daily people
     * @param positions Daily positions
     * @param scopes Monthly scopes
     * @param assignments Monthly assignments
     * @param kpis Monthly Delivery HC
     */
    public TimesheetSnapshotBrowseService(
            TimesheetPersonRepository people,
            TimesheetPositionRepository positions,
            TimesheetScopeRepository scopes,
            TimesheetAssignmentRepository assignments,
            TimesheetKpiRepository kpis) {
        this.people = people;
        this.positions = positions;
        this.scopes = scopes;
        this.assignments = assignments;
        this.kpis = kpis;
    }

    /**
     * Distinct values used by snapshot table filters.
     *
     * @return filter options
     */
    @Transactional(readOnly = true)
    public SnapshotFilters filters() {
        return new SnapshotFilters(people.findActiveCenters(), scopes.findActiveCenters(), scopes.findActiveDomains());
    }

    /**
     * @param center exact center
     * @param q name / CCGID / emp id / email / position
     * @param page 1-based page
     * @param pageSize page size
     * @return people page
     */
    @Transactional(readOnly = true)
    public PageResponse<PersonView> people(String center, String q, int page, int pageSize) {
        return PageResponse.from(
                people.searchActive(blank(center), blank(q), pageOf(page, pageSize)), this::toPerson);
    }

    /**
     * @param center exact Agent-seat center
     * @param q position id or occupant name on any of the four levels
     * @param page 1-based page
     * @param pageSize page size
     * @return one row per AGENT position with the parent chain
     */
    @Transactional(readOnly = true)
    public PageResponse<PositionView> positions(String center, String q, int page, int pageSize) {
        var rows = positions.searchActiveChains(blank(center), blank(q), pageOf(page, pageSize));
        Set<String> ids = new LinkedHashSet<>();
        for (TimesheetPositionRepository.PositionChain row : rows.getContent()) {
            addPositionId(ids, row.getAgentPositionId());
            addPositionId(ids, row.getSupervisorPositionId());
            addPositionId(ids, row.getSrManagerPositionId());
            addPositionId(ids, row.getDomainHeadPositionId());
        }
        Map<String, String> names = occupantNames(ids);
        return PageResponse.from(rows, row -> toPosition(row, names));
    }

    /**
     * @param center exact center
     * @param supervisor Supervisor position id or occupant name
     * @param pl3Code PL3 code or name fragment
     * @param page 1-based page
     * @param pageSize page size
     * @return scopes page
     */
    @Transactional(readOnly = true)
    public PageResponse<ScopeView> scopes(String center, String supervisor, String pl3Code, int page, int pageSize) {
        var rows = scopes.searchActive(blank(center), blank(supervisor), blank(pl3Code), pageOf(page, pageSize));
        Set<String> ids = new LinkedHashSet<>();
        for (TimesheetScope row : rows.getContent()) {
            addPositionId(ids, row.getSupervisorPositionId());
        }
        Map<String, String> names = occupantNames(ids);
        return PageResponse.from(rows, row -> toScope(row, names));
    }

    /**
     * @param center exact center
     * @param agent Agent position id or occupant name
     * @param supervisor Supervisor position id or occupant name
     * @param pl3Code PL3 code or name fragment
     * @param page 1-based page
     * @param pageSize page size
     * @return assignments page
     */
    @Transactional(readOnly = true)
    public PageResponse<AssignmentView> assignments(
            String center, String agent, String supervisor, String pl3Code, int page, int pageSize) {
        var rows = assignments.searchActive(
                blank(center), blank(agent), blank(supervisor), blank(pl3Code), pageOf(page, pageSize));
        return PageResponse.from(rows, row -> toAssignment(row, assignmentLookups(rows.getContent())));
    }

    /**
     * @param center exact center
     * @param supervisor Supervisor position id or occupant name
     * @param pl3Code PL3 code or name fragment
     * @param page 1-based page
     * @param pageSize page size
     * @return Delivery HC page
     */
    @Transactional(readOnly = true)
    public PageResponse<KpiView> kpis(String center, String supervisor, String pl3Code, int page, int pageSize) {
        var rows = kpis.searchActive(blank(center), blank(supervisor), blank(pl3Code), pageOf(page, pageSize));
        return PageResponse.from(rows, row -> toKpi(row, kpiLookups(rows.getContent())));
    }

    private PersonView toPerson(TimesheetPerson row) {
        return new PersonView(
                row.getCcgid(), row.getEmpId(), row.getName(), row.getEmail(), row.getCenter(), row.getPositionId());
    }

    private PositionView toPosition(
            TimesheetPositionRepository.PositionChain row, Map<String, String> names) {
        return new PositionView(
                row.getAgentPositionId(),
                names.get(row.getAgentPositionId()),
                row.getSupervisorPositionId(),
                names.get(row.getSupervisorPositionId()),
                row.getSrManagerPositionId(),
                names.get(row.getSrManagerPositionId()),
                row.getDomainHeadPositionId(),
                names.get(row.getDomainHeadPositionId()),
                row.getCenter());
    }

    private Map<String, String> occupantNames(Set<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (TimesheetPerson person : people.findActiveByPositionIdIn(ids)) {
            String positionId = person.getPositionId();
            String name = person.getName();
            if (positionId == null || positionId.isBlank() || name == null || name.isBlank()) {
                continue;
            }
            List<String> names = grouped.computeIfAbsent(positionId, key -> new ArrayList<>());
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        Map<String, String> names = new LinkedHashMap<>();
        grouped.forEach((positionId, occupants) -> names.put(positionId, String.join(", ", occupants)));
        return names;
    }

    private static void addPositionId(Set<String> ids, String positionId) {
        if (positionId != null && !positionId.isBlank()) {
            ids.add(positionId);
        }
    }

    private ScopeView toScope(TimesheetScope row, Map<String, String> names) {
        return new ScopeView(
                row.getSupervisorPositionId(),
                names.get(row.getSupervisorPositionId()),
                row.getCenter(),
                row.getDomain(),
                row.getPl1(),
                row.getPl2(),
                row.getPl3Code(),
                row.getPl3Name());
    }

    private AssignmentLookups assignmentLookups(List<TimesheetAssignment> rows) {
        Set<String> positionIds = new LinkedHashSet<>();
        Set<String> supervisorIds = new LinkedHashSet<>();
        for (TimesheetAssignment row : rows) {
            addPositionId(positionIds, row.getEmpPositionId());
            addPositionId(supervisorIds, row.getSupervisorPositionId());
        }
        Map<String, String> names = occupantNames(union(positionIds, supervisorIds));
        Map<String, String> pl3Names = new LinkedHashMap<>();
        if (!supervisorIds.isEmpty()) {
            for (TimesheetScope scope : scopes.findActiveBySupervisorPositionIdIn(supervisorIds)) {
                String key = scopeKey(scope.getSupervisorPositionId(), scope.getPl3Code(), scope.getCenter());
                if (!pl3Names.containsKey(key) && scope.getPl3Name() != null && !scope.getPl3Name().isBlank()) {
                    pl3Names.put(key, scope.getPl3Name());
                }
            }
        }
        return new AssignmentLookups(names, pl3Names);
    }

    private AssignmentView toAssignment(TimesheetAssignment row, AssignmentLookups lookups) {
        return new AssignmentView(
                row.getEmpPositionId(),
                lookups.names().get(row.getEmpPositionId()),
                row.getSupervisorPositionId(),
                lookups.names().get(row.getSupervisorPositionId()),
                row.getPl3Code(),
                lookups.pl3Names()
                        .get(scopeKey(row.getSupervisorPositionId(), row.getPl3Code(), row.getCenter())),
                row.getCenter());
    }

    private static String scopeKey(String supervisorPositionId, String pl3Code, String center) {
        return (supervisorPositionId == null ? "" : supervisorPositionId)
                + '\0'
                + (pl3Code == null ? "" : pl3Code)
                + '\0'
                + (center == null ? "" : center);
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> ids = new LinkedHashSet<>(left);
        ids.addAll(right);
        return ids;
    }

    private record AssignmentLookups(Map<String, String> names, Map<String, String> pl3Names) {
    }

    private AssignmentLookups kpiLookups(List<TimesheetKpi> rows) {
        Set<String> supervisorIds = new LinkedHashSet<>();
        for (TimesheetKpi row : rows) {
            addPositionId(supervisorIds, row.getSupervisorPositionId());
        }
        Map<String, String> names = occupantNames(supervisorIds);
        Map<String, String> pl3Names = new LinkedHashMap<>();
        if (!supervisorIds.isEmpty()) {
            for (TimesheetScope scope : scopes.findActiveBySupervisorPositionIdIn(supervisorIds)) {
                String key = scopeKey(scope.getSupervisorPositionId(), scope.getPl3Code(), scope.getCenter());
                if (!pl3Names.containsKey(key) && scope.getPl3Name() != null && !scope.getPl3Name().isBlank()) {
                    pl3Names.put(key, scope.getPl3Name());
                }
            }
        }
        return new AssignmentLookups(names, pl3Names);
    }

    private KpiView toKpi(TimesheetKpi row, AssignmentLookups lookups) {
        return new KpiView(
                row.getSupervisorPositionId(),
                lookups.names().get(row.getSupervisorPositionId()),
                row.getCenter(),
                row.getPl3Code(),
                lookups.pl3Names()
                        .get(scopeKey(row.getSupervisorPositionId(), row.getPl3Code(), row.getCenter())),
                row.getCarrier(),
                row.getSite(),
                row.getCustomerCountry(),
                row.getHc());
    }

    private static PageRequest pageOf(int page, int pageSize) {
        return PageRequest.of(Math.max(1, page) - 1, Math.min(100, Math.max(1, pageSize)));
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Distinct filter values from ACTIVE snapshots.
     */
    public record SnapshotFilters(List<String> peopleCenters, List<String> scopeCenters, List<String> scopeDomains) {
    }

    /**
     * Daily person row.
     */
    public record PersonView(
            String ccgid, String empId, String name, String email, String center, String positionId) {
    }

    /**
     * Daily AGENT seat with the walked parent chain.
     */
    public record PositionView(
            String agentPositionId,
            String agentName,
            String supervisorPositionId,
            String supervisorName,
            String srManagerPositionId,
            String srManagerName,
            String domainHeadPositionId,
            String domainHeadName,
            String center) {
    }

    /**
     * Monthly scope row.
     */
    public record ScopeView(
            String supervisorPositionId,
            String supervisorName,
            String center,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name) {
    }

    /**
     * Monthly assignment row.
     */
    public record AssignmentView(
            String agentPositionId,
            String agentName,
            String supervisorPositionId,
            String supervisorName,
            String pl3Code,
            String pl3Name,
            String center) {
    }

    /**
     * Monthly Delivery HC row.
     */
    public record KpiView(
            String supervisorPositionId,
            String supervisorName,
            String center,
            String pl3Code,
            String pl3Name,
            String carrier,
            String site,
            String customerCountry,
            BigDecimal hc) {
    }
}
