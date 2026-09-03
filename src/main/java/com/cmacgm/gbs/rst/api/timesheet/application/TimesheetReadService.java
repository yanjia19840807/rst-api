package com.cmacgm.gbs.rst.api.timesheet.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPosition;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetKpiRepository;
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
    private final TimesheetPositionRepository positions;
    private final TimesheetScopeRepository scopes;
    private final TimesheetKpiRepository kpis;

    /**
     * @param syncRuns run headers
     * @param people Daily people
     * @param positions Daily positions
     * @param scopes Monthly scopes
     * @param kpis Monthly KPIs
     */
    public TimesheetReadService(
            TimesheetSyncRunRepository syncRuns,
            TimesheetPersonRepository people,
            TimesheetPositionRepository positions,
            TimesheetScopeRepository scopes,
            TimesheetKpiRepository kpis) {
        this.syncRuns = syncRuns;
        this.people = people;
        this.positions = positions;
        this.scopes = scopes;
        this.kpis = kpis;
    }

    /**
     * @return ACTIVE Daily and Monthly headers
     */
    @Transactional(readOnly = true)
    public ActiveSnapshots activeSnapshots() {
        return new ActiveSnapshots(requireActive("DAILY"), requireActive("MONTHLY"));
    }

    /**
     * @return ACTIVE Daily snapshot
     */
    @Transactional(readOnly = true)
    public ActiveSnapshot activeDaily() {
        return requireActive("DAILY");
    }

    /**
     * @return ACTIVE Monthly snapshot
     */
    @Transactional(readOnly = true)
    public ActiveSnapshot activeMonthly() {
        return requireActive("MONTHLY");
    }

    private ActiveSnapshot requireActive(String kind) {
        TimesheetSyncRun run = syncRuns.findByKindAndStatus(kind, "ACTIVE")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "DAILY".equals(kind) ? "active-timesheet-org-missing" : "active-timesheet-kpi-missing",
                        "No ACTIVE " + kind + " Timesheet snapshot is available."));
        return new ActiveSnapshot(
                run.getId(),
                run.getKind(),
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
     * @param supervisorPositionId supervisor position
     * @param pl3Code PL3
     * @return countries
     */
    @Transactional(readOnly = true)
    public List<String> countries(String supervisorPositionId, String pl3Code) {
        return kpis.findActiveCountries(supervisorPositionId, pl3Code);
    }

    /**
     * Structural alignment of persisted KPI keys against ACTIVE Monthly.
     *
     * @param supervisorPositionId supervisor position
     * @param pl3Code PL3
     * @param keys persisted or frozen keys
     * @return alignment; missing Monthly is treated as out of scope
     */
    @Transactional(readOnly = true)
    public TimesheetAlignment align(
            String supervisorPositionId, String pl3Code, List<TimesheetAlignment.Key> keys) {
        boolean monthlyPresent = syncRuns.findByKindAndStatus("MONTHLY", "ACTIVE").isPresent();
        boolean scopePresent = monthlyPresent
                && supervisorPositionId != null
                && pl3Code != null
                && scopes.existsActiveScope(supervisorPositionId, pl3Code);
        LocalDate syncDate = findActiveMonthly().map(ActiveSnapshot::syncDate).orElse(null);
        Map<TimesheetAlignment.Key, BigDecimal> current = new LinkedHashMap<>();
        if (scopePresent) {
            for (var row : kpis.findActiveKpis(supervisorPositionId, pl3Code)) {
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
     * @param supervisorPositionId supervisor position
     * @param pl3Code PL3
     * @param countries selected countries
     * @return KPI candidates
     */
    @Transactional(readOnly = true)
    public List<KpiCandidate> kpis(
            String supervisorPositionId, String pl3Code, List<String> countries) {
        if (countries == null || countries.isEmpty()) {
            return List.of();
        }
        return kpis.findActiveKpis(supervisorPositionId, pl3Code, countries).stream()
                .map(row -> new KpiCandidate(
                        row.getCarrier(), row.getSite(), row.getCustomerCountry(), row.getHc()))
                .toList();
    }

    /**
     * @param ccgid supervisor
     * @param supervisorPositionId position
     * @param pl3Code PL3
     * @return true when Monthly scope is owned
     */
    @Transactional(readOnly = true)
    public boolean supervisorOwnsScope(
            String ccgid, String supervisorPositionId, String pl3Code) {
        return scopes.existsActiveForSupervisor(ccgid, supervisorPositionId, pl3Code);
    }

    /**
     * @param ccgid agent
     * @param supervisorPositionId toolkit supervisor position
     * @param pl3Code toolkit PL3
     * @return true when the Daily seat reports to this Supervisor and Monthly
     *     scope owns the PL3
     */
    @Transactional(readOnly = true)
    public boolean agentCanUse(
            String ccgid, String supervisorPositionId, String pl3Code) {
        return scopes.existsActiveForAgent(ccgid, supervisorPositionId, pl3Code);
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
                    ccgid -> new TeamAgent(ccgid, person.getName() == null ? ccgid : person.getName()));
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
     * Position occupied by a person for a role.
     *
     * @param ccgid occupant
     * @param roleType SUPERVISOR / SR_MANAGER
     * @return position ids
     */
    @Transactional(readOnly = true)
    public List<String> positionsForRole(String ccgid, String roleType) {
        return people.findActivePositionIdByCcgidAndRole(ccgid, roleType)
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * Occupants of a bindable position. Display name lists every occupant when
     * more than one person shares the seat; {@code ccgid} is the first by CCGID.
     *
     * @param positionId position
     * @return occupant when present
     */
    @Transactional(readOnly = true)
    public Occupant occupant(String positionId) {
        if (positionId == null || positionId.isBlank()) {
            return null;
        }
        List<TimesheetPerson> rows = people.findActiveByPositionId(positionId);
        if (rows.isEmpty()) {
            return null;
        }
        TimesheetPerson first = rows.getFirst();
        String names = rows.stream()
                .map(TimesheetPerson::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        return new Occupant(first.getPositionId(), first.getCcgid(), names.isBlank() ? first.getName() : names);
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
        return people.findActiveByCcgid(ccgid.trim())
                .map(TimesheetPerson::getPositionId)
                .filter(id -> id != null && !id.isBlank())
                .map(List::of)
                .orElse(List.of());
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
     * People across centers matching name or CCGID.
     *
     * @param query name or CCGID fragment
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
     * People in a Center who have a bindable position.
     *
     * @param center GBS center
     * @param name optional name or CCGID fragment
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
        return PageResponse.from(
                people.findActiveByCenter(center.trim(), needle, PageRequest.of(safePage - 1, safePageSize)),
                person -> new CenterPerson(person.getCcgid(), person.getName(), person.getPositionId()));
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
        return people.existsActivePositionInCenter(positionId.trim(), center.trim());
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
     * ACTIVE Daily header when one exists.
     *
     * @return daily snapshot, or empty
     */
    @Transactional(readOnly = true)
    public Optional<ActiveSnapshot> findActiveDaily() {
        return syncRuns.findByKindAndStatus("DAILY", "ACTIVE")
                .map(run -> new ActiveSnapshot(
                        run.getId(),
                        run.getKind(),
                        run.getSyncDate(),
                        run.getRowCount() == null ? 0 : run.getRowCount()));
    }

    /**
     * ACTIVE Monthly header when one exists.
     *
     * @return monthly snapshot, or empty
     */
    @Transactional(readOnly = true)
    public Optional<ActiveSnapshot> findActiveMonthly() {
        return syncRuns.findByKindAndStatus("MONTHLY", "ACTIVE")
                .map(run -> new ActiveSnapshot(
                        run.getId(),
                        run.getKind(),
                        run.getSyncDate(),
                        run.getRowCount() == null ? 0 : run.getRowCount()));
    }

    /**
     * Parent position of a position.
     *
     * @param positionId child
     * @return parent id
     */
    @Transactional(readOnly = true)
    public String parentPositionId(String positionId) {
        return positions.findActiveByPositionId(positionId)
                .map(TimesheetPosition::getParentPositionId)
                .orElse(null);
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
     * Total Monthly HC.
     *
     * @return hc
     */
    @Transactional(readOnly = true)
    public BigDecimal sumActiveHeadcount() {
        BigDecimal total = kpis.sumActiveHeadcount();
        return total == null ? BigDecimal.ZERO : total;
    }

    public record ActiveSnapshots(ActiveSnapshot org, ActiveSnapshot kpi) {
    }

    public record ActiveSnapshot(UUID id, String kind, LocalDate syncDate, int rowCount) {
    }

    public record TeamAgent(String ccgid, String name) {
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

    public record CenterPerson(String ccgid, String name, String positionId) {
    }

    public record ListedPerson(String ccgid, String name, String center) {
    }
}
