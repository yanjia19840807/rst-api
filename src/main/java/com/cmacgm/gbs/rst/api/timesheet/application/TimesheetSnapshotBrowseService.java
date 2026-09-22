package com.cmacgm.gbs.rst.api.timesheet.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPersonPositionRole;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPositionParent;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetKpiRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPersonPositionRoleRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPositionParentRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetScopeRepository;

/**
 * Paged ACTIVE snapshot tables for the Timesheet Sync monitor.
 */
@Service
public class TimesheetSnapshotBrowseService {

    private final TimesheetPersonRepository people;
    private final TimesheetPersonPositionRoleRepository seats;
    private final TimesheetPositionRepository positions;
    private final TimesheetPositionParentRepository parents;
    private final TimesheetScopeRepository scopes;
    private final TimesheetKpiRepository kpis;

    /**
     * @param people Daily people
     * @param seats Daily occupancies
     * @param positions Daily positions
     * @param parents Daily parent edges
     * @param scopes Monthly scopes
     * @param kpis Monthly Delivery HC
     */
    public TimesheetSnapshotBrowseService(
            TimesheetPersonRepository people,
            TimesheetPersonPositionRoleRepository seats,
            TimesheetPositionRepository positions,
            TimesheetPositionParentRepository parents,
            TimesheetScopeRepository scopes,
            TimesheetKpiRepository kpis) {
        this.people = people;
        this.seats = seats;
        this.positions = positions;
        this.parents = parents;
        this.scopes = scopes;
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
     * @param q name / CCGID / emp id / email / job role / position
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
     * @param center exact run center
     * @param q position, parent, role or occupant name
     * @param page 1-based page
     * @param pageSize page size
     * @return one row per position node
     */
    @Transactional(readOnly = true)
    public PageResponse<PositionView> positions(String center, String q, int page, int pageSize) {
        var rows = positions.searchActiveNodes(blank(center), blank(q), pageOf(page, pageSize));
        Map<String, String> names = occupantNamesByNode(rows.getContent());
        Map<String, List<TimesheetPositionParent>> edges = parentsByNode(blank(center), rows.getContent());
        return PageResponse.from(rows, row -> toPosition(row, names, edges));
    }

    /**
     * @param center exact run center
     * @param q name / CCGID / position / role
     * @param page 1-based page
     * @param pageSize page size
     * @return occupancies
     */
    @Transactional(readOnly = true)
    public PageResponse<OccupancyView> occupancies(String center, String q, int page, int pageSize) {
        return PageResponse.from(
                seats.searchActive(blank(center), blank(q), pageOf(page, pageSize)), this::toOccupancy);
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
     * @return assignments page derived from Daily seats × Monthly scopes
     */
    @Transactional(readOnly = true)
    public PageResponse<AssignmentView> assignments(
            String center, String agent, String supervisor, String pl3Code, int page, int pageSize) {
        var rows = positions.searchActiveAssignments(
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
                row.getCcgid(), row.getEmpId(), row.getName(), row.getEmail(), row.getJobRole(), row.getCenter());
    }

    private PositionView toPosition(
            TimesheetPositionRepository.PositionNode row,
            Map<String, String> names,
            Map<String, List<TimesheetPositionParent>> edges) {
        List<TimesheetPositionParent> parentsOfNode =
                edges.getOrDefault(nodeKey(row.getPositionId(), row.getRoleType()), List.of());
        return new PositionView(
                row.getPositionId(),
                row.getRoleType(),
                joinParent(parentsOfNode, TimesheetPositionParent::getParentPositionId),
                joinParent(parentsOfNode, TimesheetPositionParent::getParentRoleType),
                names.get(nodeKey(row.getPositionId(), row.getRoleType())),
                row.getCenter());
    }

    private Map<String, List<TimesheetPositionParent>> parentsByNode(
            String center, List<TimesheetPositionRepository.PositionNode> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (TimesheetPositionRepository.PositionNode row : rows) {
            addPositionId(ids, row.getPositionId());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, List<TimesheetPositionParent>> grouped = new LinkedHashMap<>();
        for (TimesheetPositionParent edge : parents.findActiveByPositionIdIn(center, ids)) {
            grouped.computeIfAbsent(nodeKey(edge.getPositionId(), edge.getRoleType()), key -> new ArrayList<>())
                    .add(edge);
        }
        return grouped;
    }

    private static String joinParent(
            List<TimesheetPositionParent> edges,
            java.util.function.Function<TimesheetPositionParent, String> value) {
        String joined = edges.stream().map(value).filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
        return joined.isBlank() ? null : joined;
    }

    private OccupancyView toOccupancy(TimesheetPersonPositionRoleRepository.OccupancyRow row) {
        return new OccupancyView(
                row.getCcgid(), row.getName(), row.getPositionId(), row.getRoleType(), row.getCenter());
    }

    private Map<String, String> occupantNames(Set<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        List<TimesheetPersonPositionRole> occupied = seats.findActiveByPositionIdIn(ids);
        if (occupied.isEmpty()) {
            return Map.of();
        }
        Set<String> ccgids = new LinkedHashSet<>();
        for (TimesheetPersonPositionRole seat : occupied) {
            ccgids.add(seat.getCcgid());
        }
        Map<String, String> nameByCcgid = new LinkedHashMap<>();
        for (TimesheetPerson person : people.findActiveByCcgidIn(ccgids)) {
            nameByCcgid.put(person.getCcgid().toUpperCase(Locale.ROOT), person.getName());
        }
        for (TimesheetPersonPositionRole seat : occupied) {
            String positionId = seat.getPositionId();
            String name = nameByCcgid.get(seat.getCcgid().toUpperCase(Locale.ROOT));
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

    private AssignmentLookups assignmentLookups(
            List<TimesheetPositionRepository.DerivedAssignment> rows) {
        Set<String> positionIds = new LinkedHashSet<>();
        Set<String> supervisorIds = new LinkedHashSet<>();
        for (TimesheetPositionRepository.DerivedAssignment row : rows) {
            addPositionId(positionIds, row.getAgentPositionId());
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

    private AssignmentView toAssignment(
            TimesheetPositionRepository.DerivedAssignment row, AssignmentLookups lookups) {
        String pl3Name = row.getPl3Name();
        if (pl3Name == null || pl3Name.isBlank()) {
            pl3Name = lookups.pl3Names()
                    .get(scopeKey(row.getSupervisorPositionId(), row.getPl3Code(), row.getCenter()));
        }
        return new AssignmentView(
                row.getAgentPositionId(),
                lookups.names().get(row.getAgentPositionId()),
                row.getSupervisorPositionId(),
                lookups.names().get(row.getSupervisorPositionId()),
                row.getPl3Code(),
                pl3Name,
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

    private Map<String, String> occupantNamesByNode(List<TimesheetPositionRepository.PositionNode> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (TimesheetPositionRepository.PositionNode row : rows) {
            addPositionId(ids, row.getPositionId());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        List<TimesheetPersonPositionRole> occupied = seats.findActiveByPositionIdIn(ids);
        if (occupied.isEmpty()) {
            return Map.of();
        }
        Set<String> ccgids = new LinkedHashSet<>();
        for (TimesheetPersonPositionRole seat : occupied) {
            ccgids.add(seat.getCcgid());
        }
        Map<String, String> nameByCcgid = new LinkedHashMap<>();
        for (TimesheetPerson person : people.findActiveByCcgidIn(ccgids)) {
            nameByCcgid.put(person.getCcgid().toUpperCase(Locale.ROOT), person.getName());
        }
        for (TimesheetPersonPositionRole seat : occupied) {
            String name = nameByCcgid.get(seat.getCcgid().toUpperCase(Locale.ROOT));
            if (name == null || name.isBlank()) {
                continue;
            }
            List<String> names = grouped.computeIfAbsent(
                    nodeKey(seat.getPositionId(), seat.getRoleType()), key -> new ArrayList<>());
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        Map<String, String> names = new LinkedHashMap<>();
        grouped.forEach((key, occupants) -> names.put(key, String.join(", ", occupants)));
        return names;
    }

    private static String nodeKey(String positionId, String roleType) {
        return (positionId == null ? "" : positionId) + '|' + (roleType == null ? "" : roleType);
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
     * Daily person identity.
     */
    public record PersonView(String ccgid, String empId, String name, String email, String jobRole, String center) {
    }

    /**
     * Daily position node.
     */
    public record PositionView(
            String positionId,
            String roleType,
            String parentPositionId,
            String parentRoleType,
            String occupantName,
            String center) {
    }

    /**
     * Daily occupancy.
     */
    public record OccupancyView(
            String ccgid, String name, String positionId, String roleType, String center) {
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
     * Agent seat × Supervisor Monthly PL3, derived from Daily positions.
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
