package com.cmacgm.gbs.rst.api.workforce;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.security.RstCenters;
import com.cmacgm.gbs.rst.api.sourcedata.application.CenterSourceService;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterDeliveryHc;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterSource;
import com.cmacgm.gbs.rst.api.sourcedata.persistence.CenterDeliveryHcRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetAlignment;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.ActiveSnapshot;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.CenterPerson;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.HierarchyCandidate;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.KpiCandidate;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.Occupant;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.ProductSeat;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPositionParent;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgSeat;
import com.cmacgm.gbs.rst.api.timesheet.domain.ProcessScope;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceSnapshot;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionParentRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgSeatRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.ProcessScopeRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SourceSnapshotRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * People, seats, and Delivery HC for centers that use MyHR and SKPI.
 * A Supervisor may choose any current SKPI process of their Center.
 * Delivery HC is the Center total for that process.
 */
@Service
@Transactional(readOnly = true)
public class MyHrWorkforceSource {

    private final SourceSnapshotRepository snapshots;
    private final OrgPersonRepository people;
    private final OrgSeatRepository seats;
    private final OrgPositionRepository positions;
    private final OrgPositionParentRepository parents;
    private final ProcessScopeRepository processes;
    private final CenterDeliveryHcRepository deliveryHc;
    private final CenterSourceService centerSources;

    /**
     * @param snapshots MyHR and SKPI headers
     * @param people MyHR people
     * @param seats MyHR seats
     * @param positions MyHR positions
     * @param parents MyHR parent edges
     * @param processes SKPI processes
     * @param deliveryHc SKPI Delivery HC
     * @param centerSources center source mode
     */
    public MyHrWorkforceSource(
            SourceSnapshotRepository snapshots,
            OrgPersonRepository people,
            OrgSeatRepository seats,
            OrgPositionRepository positions,
            OrgPositionParentRepository parents,
            ProcessScopeRepository processes,
            CenterDeliveryHcRepository deliveryHc,
            CenterSourceService centerSources) {
        this.snapshots = snapshots;
        this.people = people;
        this.seats = seats;
        this.positions = positions;
        this.parents = parents;
        this.processes = processes;
        this.deliveryHc = deliveryHc;
        this.centerSources = centerSources;
    }

    /**
     * @param ccgid identity
     * @return true when this person belongs to a MyHR center
     */
    public boolean serves(String ccgid) {
        OrgPerson person = person(ccgid);
        return person != null && myHrCenter(person.getCenter());
    }

    /**
     * @param positionId position or CCGID
     * @return true when the position or person belongs to a MyHR center
     */
    public boolean servesPosition(String positionId) {
        if (positionId == null || positionId.isBlank()) {
            return false;
        }
        String key = positionId.trim();
        for (OrgPerson person : people.findActiveMyHrByPositionId(key)) {
            if (myHrCenter(person.getCenter())) {
                return true;
            }
        }
        return serves(key);
    }

    /**
     * @param ccgid identity
     * @return occupied roles when the person sits in a MyHR center
     */
    public Optional<ProductSeat> productSeat(String ccgid) {
        OrgPerson person = servedPerson(ccgid);
        if (person == null) {
            return Optional.empty();
        }
        List<OrgSeat> occupied = seats.findActiveMyHrByCcgidIn(List.of(upper(person.getCcgid())));
        Set<String> roleTypes = new LinkedHashSet<>();
        for (OrgSeat seat : occupied) {
            if (seat.getRoleType() != null && !seat.getRoleType().isBlank()) {
                roleTypes.add(seat.getRoleType().trim().toUpperCase(Locale.ROOT));
            }
        }
        if (roleTypes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ProductSeat(
                Set.copyOf(roleTypes), person.getCenter(), person.getName(), person.getEmail()));
    }

    /**
     * @param ccgid identity
     * @return MyHR person when they belong to a MyHR center
     */
    public Optional<OrgPerson> findPerson(String ccgid) {
        return Optional.ofNullable(servedPerson(ccgid));
    }

    /**
     * @param ccgids identities
     * @return MyHR people whose Center uses MyHR, keyed by uppercase CCGID
     */
    public Map<String, OrgPerson> findPeople(Collection<String> ccgids) {
        if (ccgids == null || ccgids.isEmpty()) {
            return Map.of();
        }
        List<String> keys = ccgids.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::upper)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, OrgPerson> result = new HashMap<>();
        for (OrgPerson person : people.findMyHrByCcgidIn(keys)) {
            if (person.getCcgid() == null || !myHrCenter(person.getCenter())) {
                continue;
            }
            result.put(upper(person.getCcgid()), person);
        }
        return result;
    }

    /**
     * @param ccgid identity
     * @return display name, or the CCGID when the MyHR person has no name
     */
    public String displayName(String ccgid) {
        OrgPerson person = servedPerson(ccgid);
        if (person == null) {
            return ccgid == null ? null : ccgid.trim();
        }
        if (person.getName() == null || person.getName().isBlank()) {
            return person.getCcgid();
        }
        return person.getName();
    }

    /**
     * @param ccgid identity
     * @return name when the MyHR person has one
     */
    public Optional<String> findDisplayName(String ccgid) {
        OrgPerson person = servedPerson(ccgid);
        if (person == null || person.getName() == null || person.getName().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(person.getName());
    }

    /**
     * @param ccgid identity
     * @return Center when this person belongs to a MyHR center
     */
    public Optional<String> findCenter(String ccgid) {
        OrgPerson person = servedPerson(ccgid);
        if (person == null || person.getCenter() == null || person.getCenter().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(person.getCenter());
    }

    /**
     * @param ccgid identity
     * @return job role when present
     */
    public String jobRole(String ccgid) {
        OrgPerson person = servedPerson(ccgid);
        if (person == null || person.getJobRole() == null || person.getJobRole().isBlank()) {
            return null;
        }
        return person.getJobRole();
    }

    /**
     * @param center GBS center
     * @param key position or CCGID
     * @return occupant in that Center
     */
    public Occupant occupantInCenter(String center, String key) {
        String canonical = canonical(center);
        if (canonical == null || key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        List<OrgPerson> seated = people.findActiveMyHrByPositionId(trimmed).stream()
                .filter(person -> canonical.equals(canonical(person.getCenter())))
                .toList();
        if (!seated.isEmpty()) {
            return occupantOf(trimmed, seated);
        }
        return people.findMyHrByCcgidIn(List.of(upper(trimmed))).stream()
                .filter(person -> canonical.equals(canonical(person.getCenter())))
                .findFirst()
                .map(person -> new Occupant(trimmed, person.getCcgid(), person.getName()))
                .orElse(null);
    }

    /**
     * @param positionId position or CCGID
     * @return occupant when the position belongs to a MyHR center
     */
    public Occupant occupant(String positionId) {
        if (!servesPosition(positionId)) {
            return null;
        }
        String key = positionId.trim();
        List<OrgPerson> seated = people.findActiveMyHrByPositionId(key).stream()
                .filter(person -> myHrCenter(person.getCenter()))
                .toList();
        if (!seated.isEmpty()) {
            return occupantOf(key, seated);
        }
        OrgPerson person = servedPerson(key);
        return person == null ? null : new Occupant(key, person.getCcgid(), person.getName());
    }

    /**
     * @param positionId child position
     * @return parent position when exactly one edge applies
     */
    public String parentPositionId(String positionId) {
        if (positionId == null || positionId.isBlank()) {
            return null;
        }
        List<OrgPositionParent> edges = parents.findMyHrByPositionId(positionId.trim());
        List<OrgPositionParent> supervisorEdges = edges.stream()
                .filter(edge -> "SUPERVISOR".equals(edge.getRoleType()))
                .toList();
        List<OrgPositionParent> chosen = supervisorEdges.isEmpty() ? edges : supervisorEdges;
        if (chosen.size() != 1) {
            return null;
        }
        return chosen.getFirst().getParentPositionId();
    }

    /**
     * @param ccgid occupant
     * @param roleType role
     * @return position ids
     */
    public List<String> positionsForRole(String ccgid, String roleType) {
        if (!serves(ccgid) || roleType == null || roleType.isBlank()) {
            return List.of();
        }
        return seats.findActiveMyHrPositionIdsByCcgidAndRole(ccgid.trim(), roleType.trim());
    }

    /**
     * @param positionId position
     * @return roles on the MyHR position
     */
    public List<String> roleTypesOfPosition(String positionId) {
        if (!servesPosition(positionId)) {
            return List.of();
        }
        return seats.findActiveMyHrByPositionId(positionId.trim()).stream()
                .map(OrgSeat::getRoleType)
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    /**
     * @param ccgid identity
     * @return positions this person occupies
     */
    public List<String> heldPositionIds(String ccgid) {
        if (!serves(ccgid)) {
            return List.of();
        }
        return seats.findActiveMyHrByCcgidIn(List.of(upper(ccgid))).stream()
                .map(OrgSeat::getPositionId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    /**
     * @param ccgid parent occupant
     * @param childPositionId child position
     * @return true when one of the person's seats parents the position
     */
    public boolean isDirectParentOf(String ccgid, String childPositionId) {
        if (!serves(ccgid) || childPositionId == null || childPositionId.isBlank()) {
            return false;
        }
        for (OrgSeat seat : seats.findActiveMyHrByCcgidIn(List.of(upper(ccgid)))) {
            boolean parent = parents.findMyHrChildren(seat.getPositionId(), seat.getRoleType()).stream()
                    .anyMatch(edge -> childPositionId.trim().equals(edge.getPositionId()));
            if (parent) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param ccgid parent occupant
     * @return child position ids
     */
    public List<String> directChildPositionIds(String ccgid) {
        if (!serves(ccgid)) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (OrgSeat seat : seats.findActiveMyHrByCcgidIn(List.of(upper(ccgid)))) {
            for (OrgPositionParent edge : parents.findMyHrChildren(seat.getPositionId(), seat.getRoleType())) {
                if (edge.getPositionId() != null && !edge.getPositionId().isBlank()) {
                    ids.add(edge.getPositionId());
                }
            }
        }
        return List.copyOf(ids);
    }

    /**
     * @param ccgid occupant
     * @param roleTypes roles to keep, or null for every descendant role
     * @return descendant position ids
     */
    public List<String> descendantPositionIds(String ccgid, Set<String> roleTypes) {
        if (!serves(ccgid)) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        for (OrgSeat seat : seats.findActiveMyHrByCcgidIn(List.of(upper(ccgid)))) {
            if (seat.getPositionId() == null || seat.getRoleType() == null) {
                continue;
            }
            collectDescendants(seat.getPositionId(), seat.getRoleType(), roleTypes, ids, seen);
        }
        return List.copyOf(ids);
    }

    /**
     * @param center GBS center
     * @param roleType role to exclude
     * @return position ids
     */
    public List<String> positionIdsInCenterWithoutRole(String center, String roleType) {
        String canonical = canonical(center);
        if (canonical == null || roleType == null || roleType.isBlank()) {
            return List.of();
        }
        return positions.findActiveMyHrPositionIdsInCenterWithoutRole(canonical, roleType.trim());
    }

    /**
     * @param positionIds positions
     * @return uppercase CCGIDs of MyHR occupants
     */
    public List<String> occupantCcgids(Collection<String> positionIds) {
        if (positionIds == null || positionIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (OrgSeat seat : seats.findMyHrByPositionIdIn(positionIds)) {
            if (seat.getCcgid() != null && !seat.getCcgid().isBlank() && serves(seat.getCcgid())) {
                ids.add(upper(seat.getCcgid()));
            }
        }
        return List.copyOf(ids);
    }

    /**
     * @param center GBS center
     * @param name optional name, email, or CCGID fragment
     * @param page 1-based page
     * @param pageSize page size
     * @return people
     */
    public PageResponse<CenterPerson> peopleInCenter(String center, String name, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String canonical = canonical(center);
        if (canonical == null) {
            return new PageResponse<>(List.of(), safePage, safePageSize, 0, 1);
        }
        String needle = name == null ? "" : name.trim();
        var rows = people.searchActiveMyHr(canonical, needle, PageRequest.of(safePage - 1, safePageSize));
        Map<String, String> positionByCcgid = new LinkedHashMap<>();
        List<String> keys = rows.getContent().stream()
                .map(OrgPerson::getCcgid)
                .filter(id -> id != null && !id.isBlank())
                .map(this::upper)
                .distinct()
                .toList();
        if (!keys.isEmpty()) {
            for (OrgSeat seat : seats.findActiveMyHrByCcgidIn(keys)) {
                positionByCcgid.putIfAbsent(upper(seat.getCcgid()), seat.getPositionId());
            }
        }
        return PageResponse.from(
                rows,
                person -> new CenterPerson(
                        person.getCcgid(),
                        person.getName(),
                        positionByCcgid.get(upper(person.getCcgid())),
                        person.getEmail()));
    }

    /**
     * @param center GBS center
     * @param positionIds positions
     * @param name optional fragment
     * @param page 1-based page
     * @param pageSize page size
     * @return people
     */
    public PageResponse<CenterPerson> peopleOccupyingPositions(
            String center, Collection<String> positionIds, String name, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String canonical = canonical(center);
        if (canonical == null || positionIds == null || positionIds.isEmpty()) {
            return new PageResponse<>(List.of(), safePage, safePageSize, 0, 1);
        }
        String needle = name == null ? "" : name.trim();
        var rows = people.findActiveMyHrOccupantsInCenter(
                canonical, positionIds, needle, PageRequest.of(safePage - 1, safePageSize));
        return PageResponse.from(
                rows,
                person -> new CenterPerson(person.getCcgid(), person.getName(), null, person.getEmail()));
    }

    /**
     * @param ccgid identity
     * @param center GBS center
     * @return true when the person is in that MyHR center
     */
    public boolean personInCenter(String ccgid, String center) {
        String canonical = canonical(center);
        return canonical != null && ccgid != null && !ccgid.isBlank()
                && people.existsActiveMyHrInCenter(ccgid.trim(), canonical);
    }

    /**
     * @param center GBS center
     * @param positionId position
     * @return true when the position is occupied in that Center
     */
    public boolean positionInCenter(String center, String positionId) {
        String canonical = canonical(center);
        return canonical != null && positionId != null && !positionId.isBlank()
                && seats.existsActiveMyHrPositionInCenter(positionId.trim(), canonical);
    }

    /**
     * @param center GBS center
     * @return SKPI domains
     */
    public List<String> domainsInCenter(String center) {
        String canonical = canonical(center);
        if (canonical == null) {
            return List.of();
        }
        return processes.findActiveSharedDomainsByCenter(canonical);
    }

    /**
     * @param supervisorCcgid supervisor
     * @return one row per Supervisor position and SKPI process of that Center
     */
    public List<HierarchyCandidate> supervisorHierarchy(String supervisorCcgid) {
        OrgPerson person = servedPerson(supervisorCcgid);
        if (person == null) {
            return List.of();
        }
        List<String> positionIds = seats.findActiveMyHrPositionIdsByCcgidAndRole(person.getCcgid(), "SUPERVISOR");
        return hierarchy(positionIds, person.getCenter());
    }

    /**
     * @param supervisorPositionId supervisor position
     * @return SKPI processes of the position's Center
     */
    public List<HierarchyCandidate> hierarchyForPosition(String supervisorPositionId) {
        if (supervisorPositionId == null || supervisorPositionId.isBlank() || !servesPosition(supervisorPositionId)) {
            return List.of();
        }
        String positionId = supervisorPositionId.trim();
        boolean supervisor = seats.findActiveMyHrByPositionId(positionId).stream()
                .anyMatch(seat -> "SUPERVISOR".equals(seat.getRoleType()));
        if (!supervisor) {
            return List.of();
        }
        String center = people.findActiveMyHrByPositionId(positionId).stream()
                .map(OrgPerson::getCenter)
                .filter(this::myHrCenter)
                .findFirst()
                .orElse(null);
        if (center == null) {
            return List.of();
        }
        return hierarchy(List.of(positionId), center);
    }

    /**
     * @param positionId supervisor position
     * @param pl3Code process
     * @param center GBS center
     * @return true when the position is a Supervisor seat in the Center and SKPI has the process
     */
    public boolean positionHasScope(String positionId, String pl3Code, String center) {
        String canonical = canonical(center);
        if (canonical == null || positionId == null || positionId.isBlank() || pl3Code == null) {
            return false;
        }
        if (!processes.existsActiveShared(canonical, pl3Code)) {
            return false;
        }
        return seats.findActiveMyHrByPositionId(positionId.trim()).stream()
                .filter(seat -> "SUPERVISOR".equals(seat.getRoleType()))
                .anyMatch(seat -> personInCenter(seat.getCcgid(), canonical));
    }

    /**
     * @param ccgid supervisor
     * @param supervisorPositionId position
     * @param pl3Code process
     * @param center GBS center
     * @return true when this person holds that Supervisor seat and SKPI has the process
     */
    public boolean supervisorOwnsScope(
            String ccgid, String supervisorPositionId, String pl3Code, String center) {
        String canonical = canonical(center);
        if (canonical == null || !serves(ccgid) || pl3Code == null
                || supervisorPositionId == null || supervisorPositionId.isBlank()) {
            return false;
        }
        if (!processes.existsActiveShared(canonical, pl3Code)) {
            return false;
        }
        OrgPerson person = servedPerson(ccgid);
        if (person == null || !canonical.equals(canonical(person.getCenter()))) {
            return false;
        }
        return seats.findActiveMyHrPositionIdsByCcgidAndRole(person.getCcgid(), "SUPERVISOR").stream()
                .anyMatch(supervisorPositionId.trim()::equals);
    }

    /**
     * @param ccgid agent
     * @param supervisorPositionId toolkit supervisor position
     * @param pl3Code toolkit process
     * @param center GBS center
     * @return true when the Agent seat reports to that position and SKPI still has the process
     */
    public boolean agentCanUse(
            String ccgid, String supervisorPositionId, String pl3Code, String center) {
        OrgPerson person = servedPerson(ccgid);
        String canonical = canonical(center);
        if (person == null || canonical == null || !canonical.equals(canonical(person.getCenter()))
                || supervisorPositionId == null || pl3Code == null
                || !processes.existsActiveShared(canonical, pl3Code)) {
            return false;
        }
        for (OrgSeat seat : seats.findActiveMyHrByCcgidIn(List.of(upper(person.getCcgid())))) {
            if (!"AGENT".equals(seat.getRoleType())) {
                continue;
            }
            boolean reports = parents.findMyHrByPositionId(seat.getPositionId()).stream()
                    .anyMatch(edge -> "AGENT".equals(edge.getRoleType())
                            && supervisorPositionId.trim().equals(edge.getParentPositionId()));
            if (reports) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param agentPositionId covered Agent position
     * @param supervisorPositionId toolkit supervisor position
     * @param pl3Code toolkit process
     * @param center GBS center
     * @return true when that position reports to the Supervisor and SKPI still has the process
     */
    public boolean agentPositionCanUse(
            String agentPositionId, String supervisorPositionId, String pl3Code, String center) {
        String canonical = canonical(center);
        if (canonical == null || agentPositionId == null || agentPositionId.isBlank()
                || supervisorPositionId == null || pl3Code == null
                || !processes.existsActiveShared(canonical, pl3Code)
                || !seats.existsActiveMyHrPositionInCenter(agentPositionId.trim(), canonical)) {
            return false;
        }
        return parents.findMyHrByPositionId(agentPositionId.trim()).stream()
                .anyMatch(edge -> "AGENT".equals(edge.getRoleType())
                        && supervisorPositionId.trim().equals(edge.getParentPositionId()));
    }

    /**
     * @param center GBS center
     * @param pl3Code process
     * @return countries on the Center's SKPI lines
     */
    public List<String> countries(String center, String pl3Code) {
        String canonical = canonical(center);
        if (canonical == null || pl3Code == null) {
            return List.of();
        }
        return deliveryHc.findActiveCountries(canonical, pl3Code);
    }

    /**
     * @param center GBS center
     * @param pl3Code process
     * @param countries selected countries
     * @return Center Delivery HC lines
     */
    public List<KpiCandidate> kpis(String center, String pl3Code, List<String> countries) {
        String canonical = canonical(center);
        if (canonical == null || pl3Code == null || countries == null || countries.isEmpty()) {
            return List.of();
        }
        Set<String> selected = Set.copyOf(countries);
        return deliveryHc.findActiveByCenterAndPl3(canonical, pl3Code).stream()
                .filter(row -> selected.contains(row.getCustomerCountry()))
                .map(row -> new KpiCandidate(
                        row.getCarrier(), row.getSite(), row.getCustomerCountry(), row.getHc()))
                .toList();
    }

    /**
     * @param center GBS center
     * @param pl3Code process
     * @param keys persisted keys
     * @return alignment against the Center's current SKPI lines
     */
    public TimesheetAlignment align(String center, String pl3Code, List<TimesheetAlignment.Key> keys) {
        String canonical = canonical(center);
        boolean scopePresent = canonical != null && pl3Code != null
                && processes.existsActiveShared(canonical, pl3Code);
        Map<TimesheetAlignment.Key, BigDecimal> current = new LinkedHashMap<>();
        if (scopePresent) {
            for (CenterDeliveryHc row : deliveryHc.findActiveByCenterAndPl3(canonical, pl3Code)) {
                TimesheetAlignment.Key key = new TimesheetAlignment.Key(
                        row.getCarrier(), row.getSite(), row.getCustomerCountry());
                current.merge(key, row.getHc() == null ? BigDecimal.ZERO : row.getHc(), BigDecimal::add);
            }
        }
        return TimesheetAlignment.evaluate(
                scopePresent, kpiSyncDate(), keys == null ? List.of() : keys, current);
    }

    /**
     * @return current MyHR snapshot
     */
    public ActiveSnapshot requireOrg() {
        return snapshots.findByKindAndStatusAndCenter("MYHR", "ACTIVE", "")
                .map(this::toSnapshot)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "active-timesheet-org-missing",
                        "No ACTIVE MyHR snapshot is available."));
    }

    /**
     * @return current SKPI snapshot
     */
    public ActiveSnapshot requireKpi() {
        return snapshots.findByKindAndStatusAndCenter("SHARED_KPI", "ACTIVE", "")
                .map(this::toSnapshot)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "active-timesheet-kpi-missing",
                        "No ACTIVE SKPI snapshot is available."));
    }

    private List<HierarchyCandidate> hierarchy(List<String> positionIds, String center) {
        String canonical = canonical(center);
        if (canonical == null || positionIds.isEmpty()) {
            return List.of();
        }
        List<ProcessScope> rows = processes.findActiveSharedByCenter(canonical);
        List<HierarchyCandidate> result = new java.util.ArrayList<>();
        for (String positionId : positionIds) {
            for (ProcessScope row : rows) {
                result.add(new HierarchyCandidate(
                        positionId,
                        canonical,
                        row.getDomain(),
                        row.getPl1(),
                        row.getPl2(),
                        row.getPl3Code(),
                        row.getPl3Name()));
            }
        }
        return List.copyOf(result);
    }

    private void collectDescendants(
            String positionId,
            String roleType,
            Set<String> roleTypes,
            Set<String> ids,
            Set<String> seen) {
        if (!seen.add(positionId + "\0" + roleType)) {
            return;
        }
        for (OrgPositionParent edge : parents.findMyHrChildren(positionId, roleType)) {
            if (edge.getPositionId() == null || edge.getRoleType() == null) {
                continue;
            }
            if (roleTypes == null || roleTypes.contains(edge.getRoleType())) {
                ids.add(edge.getPositionId());
            }
            collectDescendants(edge.getPositionId(), edge.getRoleType(), roleTypes, ids, seen);
        }
    }

    private Occupant occupantOf(String key, List<OrgPerson> seated) {
        OrgPerson first = seated.getFirst();
        String names = seated.stream()
                .map(OrgPerson::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse(first.getName());
        return new Occupant(key, first.getCcgid(), names);
    }

    private OrgPerson servedPerson(String ccgid) {
        OrgPerson person = person(ccgid);
        if (person == null || !myHrCenter(person.getCenter())) {
            return null;
        }
        return person;
    }

    private OrgPerson person(String ccgid) {
        if (ccgid == null || ccgid.isBlank()) {
            return null;
        }
        return people.findActiveMyHrByCcgid(ccgid.trim()).orElse(null);
    }

    private boolean myHrCenter(String center) {
        String canonical = canonical(center);
        return canonical != null && CenterSource.MYHR_SKPI.equals(centerSources.sourceMode(canonical));
    }

    private java.time.LocalDate kpiSyncDate() {
        return snapshots.findByKindAndStatusAndCenter("SHARED_KPI", "ACTIVE", "")
                .map(SourceSnapshot::getSyncDate)
                .orElse(null);
    }

    private ActiveSnapshot toSnapshot(SourceSnapshot run) {
        return new ActiveSnapshot(
                run.getId(),
                run.getKind(),
                run.getCenter(),
                run.getSyncDate(),
                run.getRowCount() == null ? 0 : run.getRowCount());
    }

    private String canonical(String center) {
        return RstCenters.canonicalize(center);
    }

    private String upper(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
