package com.cmacgm.gbs.rst.api.timesheet.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPersonPositionRole;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPosition;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetKpiRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPersonPositionRoleRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetScopeRepository;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model over ACTIVE Daily org and Monthly scope / KPI snapshots.
 */
@Service
public class TimesheetReadService {

    private final TimesheetSyncRunRepository syncRuns;
    private final TimesheetPersonRepository people;
    private final TimesheetPersonPositionRoleRepository seats;
    private final TimesheetPositionRepository positions;
    private final TimesheetScopeRepository scopes;
    private final TimesheetKpiRepository kpis;

    /**
     * @param syncRuns run headers
     * @param people Daily people
     * @param seats Daily occupancies
     * @param positions Daily positions
     * @param scopes Monthly scopes
     * @param kpis Monthly KPIs
     */
    public TimesheetReadService(
            TimesheetSyncRunRepository syncRuns,
            TimesheetPersonRepository people,
            TimesheetPersonPositionRoleRepository seats,
            TimesheetPositionRepository positions,
            TimesheetScopeRepository scopes,
            TimesheetKpiRepository kpis) {
        this.syncRuns = syncRuns;
        this.people = people;
        this.seats = seats;
        this.positions = positions;
        this.scopes = scopes;
        this.kpis = kpis;
    }

    /**
     * @return ACTIVE Daily and Monthly headers, one per Center
     */
    @Transactional(readOnly = true)
    public ActiveSnapshots activeSnapshots() {
        return new ActiveSnapshots(findActive("DAILY"), findActive("MONTHLY"));
    }

    /**
     * Requires an ACTIVE Daily snapshot for the Center.
     *
     * @param center GBS center
     * @return Daily header
     */
    @Transactional(readOnly = true)
    public ActiveSnapshot activeDaily(String center) {
        return requireActive("DAILY", center);
    }

    /**
     * Requires an ACTIVE Monthly snapshot for the Center.
     *
     * @param center GBS center
     * @return Monthly header
     */
    @Transactional(readOnly = true)
    public ActiveSnapshot activeMonthly(String center) {
        return requireActive("MONTHLY", center);
    }

    private ActiveSnapshot requireActive(String kind, String center) {
        return findActive(kind, center)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "DAILY".equals(kind) ? "active-timesheet-org-missing" : "active-timesheet-kpi-missing",
                        "No ACTIVE " + kind + " Timesheet snapshot is available"
                                + (center == null || center.isBlank() ? "." : " for " + center + ".")));
    }

    private Optional<ActiveSnapshot> findActive(String kind, String center) {
        if (center == null || center.isBlank()) {
            return Optional.empty();
        }
        return syncRuns.findByKindAndStatusAndCenter(kind, "ACTIVE", center.trim()).map(this::toSnapshot);
    }

    private List<ActiveSnapshot> findActive(String kind) {
        return syncRuns.findByKindAndStatus(kind, "ACTIVE").stream()
                .map(this::toSnapshot)
                .toList();
    }

    private ActiveSnapshot toSnapshot(TimesheetSyncRun run) {
        return new ActiveSnapshot(
                run.getId(),
                run.getKind(),
                run.getCenter(),
                run.getSyncDate(),
                run.getRowCount() == null ? 0 : run.getRowCount());
    }

    /**
     * Toolkit hierarchy for a Supervisor occupant.
     *
     * @param supervisorCcgid supervisor
     * @return scopes
     */
    @Transactional(readOnly = true)
    public List<HierarchyCandidate> supervisorHierarchy(String supervisorCcgid) {
        return scopes.findActiveBySupervisorCcgid(supervisorCcgid).stream()
                .map(scope -> new HierarchyCandidate(
                        scope.getSupervisorPositionId(),
                        scope.getCenter(),
                        scope.getDomain(),
                        scope.getPl1(),
                        scope.getPl2(),
                        scope.getPl3Code(),
                        scope.getPl3Name()))
                .toList();
    }

    /**
     * Shared KPI countries from Monthly.
     *
     * @param center GBS center
     * @param supervisorPositionId supervisor position
     * @param pl3Code PL3
     * @return countries
     */
    @Transactional(readOnly = true)
    public List<String> countries(String center, String supervisorPositionId, String pl3Code) {
        if (center == null || center.isBlank()) {
            return List.of();
        }
        return kpis.findActiveCountries(supervisorPositionId, pl3Code, center.trim());
    }

    /**
     * Structural alignment of persisted KPI keys against ACTIVE Monthly.
     *
     * @param center GBS center
     * @param supervisorPositionId supervisor position
     * @param pl3Code PL3
     * @param keys persisted or frozen keys
     * @return alignment; missing Monthly is treated as out of scope
     */
    @Transactional(readOnly = true)
    public TimesheetAlignment align(
            String center, String supervisorPositionId, String pl3Code, List<TimesheetAlignment.Key> keys) {
        boolean monthlyPresent = findActive("MONTHLY", center).isPresent();
        boolean scopePresent = monthlyPresent
                && supervisorPositionId != null
                && pl3Code != null
                && scopes.existsActiveScope(supervisorPositionId, pl3Code, center.trim());
        LocalDate syncDate = findActiveMonthlySyncDate(center, supervisorPositionId, pl3Code);
        Map<TimesheetAlignment.Key, BigDecimal> current = new LinkedHashMap<>();
        if (scopePresent) {
            for (var row : kpis.findActiveKpis(supervisorPositionId, pl3Code, center.trim())) {
                TimesheetAlignment.Key key = new TimesheetAlignment.Key(
                        row.getCarrier(), row.getSite(), row.getCustomerCountry());
                current.merge(key, row.getHc() == null ? BigDecimal.ZERO : row.getHc(), BigDecimal::add);
            }
        }
        return TimesheetAlignment.evaluate(scopePresent, syncDate, keys == null ? List.of() : keys, current);
    }

    /**
     * Shared KPI rows from Monthly.
     *
     * @param center GBS center
     * @param supervisorPositionId supervisor position
     * @param pl3Code PL3
     * @param countries selected countries
     * @return KPI candidates
     */
    @Transactional(readOnly = true)
    public List<KpiCandidate> kpis(
            String center, String supervisorPositionId, String pl3Code, List<String> countries) {
        if (center == null || center.isBlank() || countries == null || countries.isEmpty()) {
            return List.of();
        }
        return kpis.findActiveKpis(supervisorPositionId, pl3Code, countries, center.trim()).stream()
                .map(row -> new KpiCandidate(
                        row.getCarrier(), row.getSite(), row.getCustomerCountry(), row.getHc()))
                .toList();
    }

    /**
     * @param ccgid supervisor
     * @param supervisorPositionId position
     * @param pl3Code PL3
     * @param center GBS center
     * @return true when Monthly scope is owned
     */
    @Transactional(readOnly = true)
    public boolean supervisorOwnsScope(
            String ccgid, String supervisorPositionId, String pl3Code, String center) {
        if (center == null || center.isBlank()) {
            return false;
        }
        return scopes.existsActiveForSupervisor(ccgid, supervisorPositionId, pl3Code, center.trim());
    }

    /**
     * @param ccgid agent
     * @param supervisorPositionId toolkit supervisor position
     * @param pl3Code toolkit PL3
     * @param center GBS center
     * @return true when the Daily seat reports to this Supervisor and Monthly
     *     scope owns the PL3
     */
    @Transactional(readOnly = true)
    public boolean agentCanUse(
            String ccgid, String supervisorPositionId, String pl3Code, String center) {
        if (center == null || center.isBlank()) {
            return false;
        }
        return scopes.existsActiveForAgent(ccgid, supervisorPositionId, pl3Code, center.trim());
    }

    /**
     * Distinct team agents under a Supervisor.
     *
     * @param supervisorCcgid supervisor
     * @return agents
     */
    @Transactional(readOnly = true)
    public List<TeamAgent> teamAgents(String supervisorCcgid) {
        LinkedHashMap<String, TeamAgent> unique = new LinkedHashMap<>();
        for (TimesheetPerson person : people.findActiveReportsBySupervisorCcgid(supervisorCcgid)) {
            unique.computeIfAbsent(
                    person.getCcgid(),
                    ccgid -> new TeamAgent(
                            ccgid,
                            person.getName() == null ? ccgid : person.getName(),
                            person.getEmail()));
        }
        return List.copyOf(unique.values());
    }

    /**
     * Display name from ACTIVE Daily person, else the ccgid.
     *
     * @param ccgid identity
     * @return name
     */
    @Transactional(readOnly = true)
    public String displayNameByCcgid(String ccgid) {
        if (ccgid == null || ccgid.isBlank()) {
            return null;
        }
        String trimmed = ccgid.trim();
        return findDisplayName(trimmed).orElse(trimmed);
    }

    /**
     * Display name from ACTIVE Daily person when the identity exists.
     *
     * @param ccgid identity
     * @return name when present
     */
    @Transactional(readOnly = true)
    public Optional<String> findDisplayName(String ccgid) {
        if (ccgid == null || ccgid.isBlank()) {
            return Optional.empty();
        }
        return people.findActiveNameByCcgid(ccgid.trim());
    }

    /**
     * GBS Center for an ACTIVE Daily identity. Uses the person row first, then
     * the occupied position when the person Center is blank.
     *
     * @param ccgid identity
     * @return center when present
     */
    @Transactional(readOnly = true)
    public Optional<String> findActiveCenter(String ccgid) {
        if (ccgid == null || ccgid.isBlank()) {
            return Optional.empty();
        }
        return people.findActiveByCcgid(ccgid.trim()).flatMap(person -> {
            String fromPerson = trimToNull(person.getCenter());
            if (fromPerson != null) {
                return Optional.of(fromPerson);
            }
            return syncRuns.findById(person.getSyncRunId())
                    .map(TimesheetSyncRun::getCenter)
                    .map(this::trimToNull);
        });
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Position occupied by a person for a role.
     *
     * @param ccgid occupant
     * @param roleType SUPERVISOR / SR_MANAGER
     * @return position ids
     */
    @Transactional(readOnly = true)
    public List<String> positionsForRole(String ccgid, String roleType) {
        if (ccgid == null || ccgid.isBlank() || roleType == null || roleType.isBlank()) {
            return List.of();
        }
        return seats.findActivePositionIdsByCcgidAndRole(ccgid.trim(), roleType.trim());
    }

    /**
     * Occupants of a bindable position. Display name lists every occupant when
     * more than one person shares the seat; {@code ccgid} is the first by CCGID.
     * When {@code positionId} is itself a CCGID with no seat, the person identity
     * is returned so Center Roles can assign people who are in the Center but
     * not on an RST Production line.
     *
     * @param positionId position or CCGID
     * @return occupant when present
     */
    @Transactional(readOnly = true)
    public Occupant occupant(String positionId) {
        if (positionId == null || positionId.isBlank()) {
            return null;
        }
        List<TimesheetPerson> rows = people.findActiveByPositionId(positionId);
        if (rows.isEmpty()) {
            return people.findActiveByCcgid(positionId.trim())
                    .map(person -> new Occupant(positionId, person.getCcgid(), person.getName()))
                    .orElse(null);
        }
        TimesheetPerson first = rows.getFirst();
        String names = rows.stream()
                .map(TimesheetPerson::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        return new Occupant(positionId, first.getCcgid(), names.isBlank() ? first.getName() : names);
    }

    /**
     * Position this person currently occupies.
     *
     * @param ccgid identity
     * @return position ids
     */
    @Transactional(readOnly = true)
    public List<String> heldPositionIds(String ccgid) {
        if (ccgid == null || ccgid.isBlank()) {
            return List.of();
        }
        return seats.findActiveByCcgid(ccgid.trim()).stream()
                .map(TimesheetPersonPositionRole::getPositionId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    /**
     * Timesheet seat used when SSO NAME is {@code USER}.
     *
     * @param ccgid identity
     * @return roleType, center, and display fields when the person occupies a position
     */
    @Transactional(readOnly = true)
    public Optional<ProductSeat> findActiveProductSeat(String ccgid) {
        return findActivePerson(ccgid).flatMap(person -> {
            List<TimesheetPersonPositionRole> occupied = seats.findActiveByCcgid(person.getCcgid());
            if (occupied.isEmpty()) {
                return Optional.empty();
            }
            Set<String> roleTypes = new LinkedHashSet<>();
            for (TimesheetPersonPositionRole seat : occupied) {
                if (seat.getRoleType() != null && !seat.getRoleType().isBlank()) {
                    roleTypes.add(seat.getRoleType().trim().toUpperCase(Locale.ROOT));
                }
            }
            if (roleTypes.isEmpty()) {
                return Optional.empty();
            }
            String center = firstNonBlank(
                    person.getCenter(),
                    syncRuns.findById(person.getSyncRunId()).map(TimesheetSyncRun::getCenter).orElse(null));
            return Optional.of(new ProductSeat(Set.copyOf(roleTypes), center, person.getName(), person.getEmail()));
        });
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? null : second.trim();
    }

    /**
     * Active Daily person by CCGID.
     *
     * @param ccgid identity
     * @return person when present
     */
    @Transactional(readOnly = true)
    public Optional<TimesheetPerson> findActivePerson(String ccgid) {
        if (ccgid == null || ccgid.isBlank()) {
            return Optional.empty();
        }
        return people.findActiveByCcgid(ccgid.trim());
    }

    /**
     * ACTIVE Daily people for the given CCGIDs, keyed by uppercase CCGID.
     *
     * @param ccgids identities
     * @return people
     */
    @Transactional(readOnly = true)
    public Map<String, TimesheetPerson> findActivePeopleByCcgids(Collection<String> ccgids) {
        if (ccgids == null || ccgids.isEmpty()) {
            return Map.of();
        }
        List<String> keys = ccgids.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, TimesheetPerson> result = new HashMap<>();
        for (TimesheetPerson person : people.findActiveByCcgidIn(keys)) {
            if (person.getCcgid() == null || person.getCcgid().isBlank()) {
                continue;
            }
            result.put(person.getCcgid().toUpperCase(Locale.ROOT), person);
        }
        return result;
    }

    /**
     * People across centers matching name, email or CCGID.
     *
     * @param query name / email / CCGID fragment
     * @param page 1-based page
     * @param pageSize page size
     * @return people
     */
    @Transactional(readOnly = true)
    public PageResponse<ListedPerson> searchActivePeople(String query, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String needle = query == null ? "" : query.trim();
        return PageResponse.from(
                people.findActiveByNameOrCcgid(needle, PageRequest.of(safePage - 1, safePageSize)),
                person -> new ListedPerson(person.getCcgid(), person.getName(), person.getCenter()));
    }

    /**
     * People in a Center. {@code positionId} is filled when they occupy a
     * bindable seat; otherwise it is null.
     *
     * @param center GBS center
     * @param name optional name / email / CCGID fragment
     * @param page 1-based page
     * @param pageSize page size
     * @return people
     */
    @Transactional(readOnly = true)
    public PageResponse<CenterPerson> peopleInCenter(String center, String name, int page, int pageSize) {
        if (center == null || center.isBlank()) {
            int safePage = Math.max(1, page);
            int safePageSize = Math.min(100, Math.max(1, pageSize));
            return new PageResponse<>(List.of(), safePage, safePageSize, 0, 1);
        }
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String needle = name == null ? "" : name.trim();
        var rows = people.findActiveByCenter(center.trim(), needle, PageRequest.of(safePage - 1, safePageSize));
        Map<String, String> positionByCcgid = positionIdsByCcgid(
                rows.getContent().stream().map(TimesheetPerson::getCcgid).toList());
        return PageResponse.from(
                rows,
                person -> new CenterPerson(
                        person.getCcgid(),
                        person.getName(),
                        positionByCcgid.get(person.getCcgid().toUpperCase(Locale.ROOT)),
                        person.getEmail()));
    }

    /**
     * Whether this bindable position belongs to the Center.
     *
     * @param center GBS center
     * @param positionId emp or occupied management position
     * @return true when present
     */
    @Transactional(readOnly = true)
    public boolean positionInCenter(String center, String positionId) {
        if (center == null || center.isBlank() || positionId == null || positionId.isBlank()) {
            return false;
        }
        return seats.existsActivePositionInCenter(positionId.trim(), center.trim());
    }

    /**
     * Distinct domains present for a Center in ACTIVE Monthly scope.
     *
     * @param center GBS center
     * @return domains
     */
    @Transactional(readOnly = true)
    public List<String> domainsInCenter(String center) {
        if (center == null || center.isBlank()) {
            return List.of();
        }
        return scopes.findActiveDomainsByCenter(center.trim());
    }

    /**
     * Whether this person still appears in the Center.
     *
     * @param ccgid identity
     * @param center GBS center
     * @return true when present
     */
    @Transactional(readOnly = true)
    public boolean personInCenter(String ccgid, String center) {
        if (ccgid == null || ccgid.isBlank() || center == null || center.isBlank()) {
            return false;
        }
        return people.existsActiveInCenter(ccgid.trim(), center.trim());
    }

    /**
     * Distinct GBS centers from ACTIVE Daily people and Monthly scopes (union, sorted).
     *
     * @return centers
     */
    @Transactional(readOnly = true)
    public List<String> activeCenters() {
        return java.util.stream.Stream.concat(
                        people.findActiveCenters().stream(), scopes.findActiveCenters().stream())
                .filter(center -> center != null && !center.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * ACTIVE Daily header for a Center when one exists.
     *
     * @param center GBS center
     * @return daily snapshot, or empty
     */
    @Transactional(readOnly = true)
    public Optional<ActiveSnapshot> findActiveDaily(String center) {
        return findActive("DAILY", center);
    }

    /**
     * ACTIVE Monthly header for a Center when one exists.
     *
     * @param center GBS center
     * @return monthly snapshot, or empty
     */
    @Transactional(readOnly = true)
    public Optional<ActiveSnapshot> findActiveMonthly(String center) {
        return findActive("MONTHLY", center);
    }

    private LocalDate findActiveMonthlySyncDate(String center, String supervisorPositionId, String pl3Code) {
        if (center == null || center.isBlank() || supervisorPositionId == null || pl3Code == null) {
            return null;
        }
        return kpis.findActiveKpis(supervisorPositionId, pl3Code, center.trim()).stream()
                .map(TimesheetKpi::getSyncRunId)
                .findFirst()
                .flatMap(syncRuns::findById)
                .map(TimesheetSyncRun::getSyncDate)
                .orElse(null);
    }

    /**
     * Parent position of a position.
     *
     * @param positionId child
     * @return parent id
     */
    @Transactional(readOnly = true)
    public String parentPositionId(String positionId) {
        if (positionId == null || positionId.isBlank()) {
            return null;
        }
        List<TimesheetPosition> nodes = positions.findActiveByPositionId(positionId.trim());
        return nodes.stream()
                .filter(position -> "SUPERVISOR".equals(position.getRoleType()))
                .map(TimesheetPosition::getParentPositionId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElseGet(() -> nodes.stream()
                        .map(TimesheetPosition::getParentPositionId)
                        .filter(id -> id != null && !id.isBlank())
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Job title from the person's ACTIVE Daily identity.
     *
     * @param ccgid identity
     * @return emp_job_role when present
     */
    @Transactional(readOnly = true)
    public String findActiveJobRole(String ccgid) {
        if (ccgid == null || ccgid.isBlank()) {
            return null;
        }
        return people.findActiveByCcgid(ccgid.trim())
                .map(TimesheetPerson::getJobRole)
                .filter(value -> value != null && !value.isBlank())
                .orElse(null);
    }

    private Map<String, String> positionIdsByCcgid(List<String> ccgids) {
        if (ccgids == null || ccgids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> positionByCcgid = new LinkedHashMap<>();
        for (TimesheetPersonPositionRole seat : seats.findActiveByCcgidIn(ccgids)) {
            positionByCcgid.putIfAbsent(seat.getCcgid().toUpperCase(Locale.ROOT), seat.getPositionId());
        }
        return positionByCcgid;
    }

    /**
     * Dashboard obligations from Monthly scope.
     *
     * @return scopes
     */
    @Transactional(readOnly = true)
    public List<TimesheetScope> dashboardObligations() {
        return scopes.findActiveDashboardObligations();
    }

    /**
     * Dashboard universe: ACTIVE Monthly Delivery HC rows.
     *
     * @return KPI rows
     */
    @Transactional(readOnly = true)
    public List<TimesheetKpi> dashboardKpis() {
        return kpis.findActiveDashboardKpis();
    }

    /**
     * Total Monthly HC.
     *
     * @return hc
     */
    @Transactional(readOnly = true)
    public BigDecimal sumActiveHeadcount() {
        BigDecimal total = kpis.sumActiveHeadcount();
        return total == null ? BigDecimal.ZERO : total;
    }

    public record ActiveSnapshots(List<ActiveSnapshot> org, List<ActiveSnapshot> kpi) {
    }

    public record ActiveSnapshot(UUID id, String kind, String center, LocalDate syncDate, int rowCount) {
    }

    public record TeamAgent(String ccgid, String name, String email) {
    }

    public record HierarchyCandidate(
            String supervisorPositionId,
            String center,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name) {
    }

    public record KpiCandidate(
            String carrier, String site, String customerCountry, BigDecimal deliveryHc) {
    }

    public record Occupant(String positionId, String ccgid, String name) {
    }

    public record CenterPerson(String ccgid, String name, String positionId, String email) {
    }

    public record ListedPerson(String ccgid, String name, String center) {
    }

    /**
     * Occupied Timesheet seats for an SSO USER login.
     *
     * @param roleTypes AGENT / SUPERVISOR / SR_MANAGER held by the person
     * @param center GBS center
     * @param displayName Timesheet name
     * @param email Timesheet email
     */
    public record ProductSeat(Set<String> roleTypes, String center, String displayName, String email) {
    }
}
