package com.cmacgm.gbs.rst.api.workforce;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.security.RstCenters;
import com.cmacgm.gbs.rst.api.sourcedata.application.CenterSourceService;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterSource;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetAlignment;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.ActiveSnapshot;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.CenterPerson;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.HierarchyCandidate;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.KpiCandidate;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.Occupant;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.ProductSeat;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPerson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routes org and Delivery HC reads by the Center's data source.
 * Timesheet centers stay on Daily and Monthly. MyHR centers use MyHR seats and SKPI.
 * A person who still has an old Daily row follows the Center's current source.
 */
@Service
@Transactional(readOnly = true)
public class WorkforceRead {

    private final TimesheetReadService timesheet;
    private final MyHrWorkforceSource myHr;
    private final CenterSourceService centerSources;

    /**
     * @param timesheet Daily and Monthly reads
     * @param myHr MyHR and SKPI reads
     * @param centerSources center source mode
     */
    public WorkforceRead(
            TimesheetReadService timesheet,
            MyHrWorkforceSource myHr,
            CenterSourceService centerSources) {
        this.timesheet = timesheet;
        this.myHr = myHr;
        this.centerSources = centerSources;
    }

    /**
     * @param center GBS center
     * @return true when the Center uses MyHR and SKPI
     */
    public boolean myHrCenter(String center) {
        String canonical = RstCenters.canonicalize(center);
        return canonical != null && CenterSource.MYHR_SKPI.equals(centerSources.sourceMode(canonical));
    }

    /**
     * @param ccgid identity
     * @return occupied product seat from the Center's current source
     */
    public Optional<ProductSeat> findActiveProductSeat(String ccgid) {
        if (myHr.serves(ccgid)) {
            return myHr.productSeat(ccgid);
        }
        return timesheet.findActiveProductSeat(ccgid).filter(seat -> !myHrCenter(seat.center()));
    }

    /**
     * @param ccgid identity
     * @return person from the Center's current source
     */
    public Optional<OrgPerson> findActivePerson(String ccgid) {
        if (myHr.serves(ccgid)) {
            return myHr.findPerson(ccgid);
        }
        return timesheet.findActivePerson(ccgid).filter(person -> !myHrCenter(person.getCenter()));
    }

    /**
     * @param ccgids identities
     * @return people from each identity's current source
     */
    public Map<String, OrgPerson> findActivePeopleByCcgids(Collection<String> ccgids) {
        Map<String, OrgPerson> myHrPeople = myHr.findPeople(ccgids);
        Map<String, OrgPerson> result = new LinkedHashMap<>(myHrPeople);
        timesheet.findActivePeopleByCcgids(ccgids).forEach((ccgid, person) -> {
            if (!myHrCenter(person.getCenter())) {
                result.putIfAbsent(ccgid, person);
            }
        });
        return result;
    }

    /**
     * @param ccgid identity
     * @return display name from the current source, else the CCGID
     */
    public String displayNameByCcgid(String ccgid) {
        if (myHr.serves(ccgid)) {
            return myHr.displayName(ccgid);
        }
        return timesheet.displayNameByCcgid(ccgid);
    }

    /**
     * @param ccgid identity
     * @return name when the current source has one
     */
    public Optional<String> findDisplayName(String ccgid) {
        if (myHr.serves(ccgid)) {
            return myHr.findDisplayName(ccgid);
        }
        return timesheet.findDisplayName(ccgid);
    }

    /**
     * @param ccgid identity
     * @return Center from the current source
     */
    public Optional<String> findActiveCenter(String ccgid) {
        if (myHr.serves(ccgid)) {
            return myHr.findCenter(ccgid);
        }
        return timesheet.findActiveCenter(ccgid).filter(center -> !myHrCenter(center));
    }

    /**
     * @param ccgid identity
     * @return job role from the current source
     */
    public String findActiveJobRole(String ccgid) {
        if (myHr.serves(ccgid)) {
            return myHr.jobRole(ccgid);
        }
        return timesheet.findActiveJobRole(ccgid);
    }

    /**
     * @param center GBS center
     * @param key position or CCGID
     * @return occupant in that Center's current source
     */
    public Occupant occupantInCenter(String center, String key) {
        if (myHrCenter(center)) {
            return myHr.occupantInCenter(center, key);
        }
        return timesheet.occupant(key);
    }

    /**
     * @param positionId position or CCGID
     * @return occupant from the current source
     */
    public Occupant occupant(String positionId) {
        if (myHr.servesPosition(positionId)) {
            return myHr.occupant(positionId);
        }
        return timesheet.occupant(positionId);
    }

    /**
     * @param positionId child position
     * @return parent position from the current source
     */
    public String parentPositionId(String positionId) {
        if (myHr.servesPosition(positionId)) {
            return myHr.parentPositionId(positionId);
        }
        return timesheet.parentPositionId(positionId);
    }

    /**
     * @param ccgid occupant
     * @param roleType role
     * @return position ids
     */
    public List<String> positionsForRole(String ccgid, String roleType) {
        if (myHr.serves(ccgid)) {
            return myHr.positionsForRole(ccgid, roleType);
        }
        return timesheet.positionsForRole(ccgid, roleType);
    }

    /**
     * @param positionId position
     * @return roles
     */
    public List<String> roleTypesOfPosition(String positionId) {
        if (myHr.servesPosition(positionId)) {
            return myHr.roleTypesOfPosition(positionId);
        }
        return timesheet.roleTypesOfPosition(positionId);
    }

    /**
     * @param ccgid identity
     * @return occupied position ids
     */
    public List<String> heldPositionIds(String ccgid) {
        if (myHr.serves(ccgid)) {
            return myHr.heldPositionIds(ccgid);
        }
        return timesheet.heldPositionIds(ccgid);
    }

    /**
     * @param ccgid parent occupant
     * @param childPositionId child position
     * @return true when the current source has that parent edge
     */
    public boolean isDirectParentOf(String ccgid, String childPositionId) {
        if (myHr.serves(ccgid)) {
            return myHr.isDirectParentOf(ccgid, childPositionId);
        }
        return timesheet.isDirectParentOf(ccgid, childPositionId);
    }

    /**
     * @param ccgid parent occupant
     * @return child position ids
     */
    public List<String> directChildPositionIds(String ccgid) {
        if (myHr.serves(ccgid)) {
            return myHr.directChildPositionIds(ccgid);
        }
        return timesheet.directChildPositionIds(ccgid);
    }

    /**
     * @param ccgid occupant
     * @param roleTypes roles to keep, or null for every descendant role
     * @return descendant position ids
     */
    public List<String> descendantPositionIds(String ccgid, Set<String> roleTypes) {
        if (myHr.serves(ccgid)) {
            return myHr.descendantPositionIds(ccgid, roleTypes);
        }
        return timesheet.descendantPositionIds(ccgid, roleTypes);
    }

    /**
     * @param center GBS center
     * @param roleType role to exclude
     * @return position ids
     */
    public List<String> positionIdsInCenterWithoutRole(String center, String roleType) {
        if (myHrCenter(center)) {
            return myHr.positionIdsInCenterWithoutRole(center, roleType);
        }
        return timesheet.positionIdsInCenterWithoutRole(center, roleType);
    }

    /**
     * @param positionIds positions
     * @return uppercase CCGIDs from whichever source holds each position
     */
    public List<String> occupantCcgids(Collection<String> positionIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (positionIds == null || positionIds.isEmpty()) {
            return List.of();
        }
        List<String> myHrPositions = positionIds.stream().filter(myHr::servesPosition).toList();
        List<String> dailyPositions = positionIds.stream().filter(id -> !myHr.servesPosition(id)).toList();
        ids.addAll(myHr.occupantCcgids(myHrPositions));
        ids.addAll(timesheet.occupantCcgids(dailyPositions));
        return List.copyOf(ids);
    }

    /**
     * @param center GBS center
     * @param name optional fragment
     * @param page 1-based page
     * @param pageSize page size
     * @return people
     */
    public PageResponse<CenterPerson> peopleInCenter(String center, String name, int page, int pageSize) {
        if (myHrCenter(center)) {
            return myHr.peopleInCenter(center, name, page, pageSize);
        }
        return timesheet.peopleInCenter(center, name, page, pageSize);
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
        if (myHrCenter(center)) {
            return myHr.peopleOccupyingPositions(center, positionIds, name, page, pageSize);
        }
        return timesheet.peopleOccupyingPositions(center, positionIds, name, page, pageSize);
    }

    /**
     * @param ccgid identity
     * @param center GBS center
     * @return true when the person is in that Center's current source
     */
    public boolean personInCenter(String ccgid, String center) {
        if (myHrCenter(center)) {
            return myHr.personInCenter(ccgid, center);
        }
        return timesheet.personInCenter(ccgid, center);
    }

    /**
     * @param center GBS center
     * @param positionId position
     * @return true when the position belongs to that Center's current source
     */
    public boolean positionInCenter(String center, String positionId) {
        if (myHrCenter(center)) {
            return myHr.positionInCenter(center, positionId);
        }
        return timesheet.positionInCenter(center, positionId);
    }

    /**
     * @param center GBS center
     * @return domains from Monthly or SKPI
     */
    public List<String> domainsInCenter(String center) {
        if (myHrCenter(center)) {
            return myHr.domainsInCenter(center);
        }
        return timesheet.domainsInCenter(center);
    }

    /**
     * @param supervisorCcgid supervisor
     * @return processes this Supervisor can select
     */
    public List<HierarchyCandidate> supervisorHierarchy(String supervisorCcgid) {
        if (myHr.serves(supervisorCcgid)) {
            return myHr.supervisorHierarchy(supervisorCcgid);
        }
        return timesheet.supervisorHierarchy(supervisorCcgid).stream()
                .filter(item -> !myHrCenter(item.center()))
                .toList();
    }

    /**
     * @param supervisorPositionId supervisor position
     * @return processes that position can select
     */
    public List<HierarchyCandidate> hierarchyForPosition(String supervisorPositionId) {
        if (myHr.servesPosition(supervisorPositionId)) {
            return myHr.hierarchyForPosition(supervisorPositionId);
        }
        return timesheet.hierarchyForPosition(supervisorPositionId).stream()
                .filter(item -> !myHrCenter(item.center()))
                .toList();
    }

    /**
     * @param positionId supervisor position
     * @param pl3Code process
     * @param center GBS center
     * @return true when the position can still use the process
     */
    public boolean positionHasScope(String positionId, String pl3Code, String center) {
        if (myHrCenter(center)) {
            return myHr.positionHasScope(positionId, pl3Code, center);
        }
        return timesheet.positionHasScope(positionId, pl3Code, center);
    }

    /**
     * @param ccgid supervisor
     * @param supervisorPositionId position
     * @param pl3Code process
     * @param center GBS center
     * @return true when this Supervisor can use the process
     */
    public boolean supervisorOwnsScope(
            String ccgid, String supervisorPositionId, String pl3Code, String center) {
        if (myHrCenter(center)) {
            return myHr.supervisorOwnsScope(ccgid, supervisorPositionId, pl3Code, center);
        }
        return timesheet.supervisorOwnsScope(ccgid, supervisorPositionId, pl3Code, center);
    }

    /**
     * @param ccgid agent
     * @param supervisorPositionId toolkit supervisor position
     * @param pl3Code process
     * @param center GBS center
     * @return true when the Agent can use the Toolkit
     */
    public boolean agentCanUse(
            String ccgid, String supervisorPositionId, String pl3Code, String center) {
        if (myHrCenter(center)) {
            return myHr.agentCanUse(ccgid, supervisorPositionId, pl3Code, center);
        }
        return timesheet.agentCanUse(ccgid, supervisorPositionId, pl3Code, center);
    }

    /**
     * @param agentPositionId covered Agent position
     * @param supervisorPositionId toolkit supervisor position
     * @param pl3Code process
     * @param center GBS center
     * @return true when the covered position can use the Toolkit
     */
    public boolean agentPositionCanUse(
            String agentPositionId, String supervisorPositionId, String pl3Code, String center) {
        if (myHrCenter(center)) {
            return myHr.agentPositionCanUse(agentPositionId, supervisorPositionId, pl3Code, center);
        }
        return timesheet.agentPositionCanUse(agentPositionId, supervisorPositionId, pl3Code, center);
    }

    /**
     * @param center GBS center
     * @param supervisorPositionId supervisor position; ignored for a MyHR center
     * @param pl3Code process
     * @return countries
     */
    public List<String> countries(String center, String supervisorPositionId, String pl3Code) {
        if (myHrCenter(center)) {
            return myHr.countries(center, pl3Code);
        }
        return timesheet.countries(center, supervisorPositionId, pl3Code);
    }

    /**
     * @param center GBS center
     * @param supervisorPositionId supervisor position; ignored for a MyHR center
     * @param pl3Code process
     * @param countries selected countries
     * @return Delivery HC candidates
     */
    public List<KpiCandidate> kpis(
            String center, String supervisorPositionId, String pl3Code, List<String> countries) {
        if (myHrCenter(center)) {
            return myHr.kpis(center, pl3Code, countries);
        }
        return timesheet.kpis(center, supervisorPositionId, pl3Code, countries);
    }

    /**
     * @param center GBS center
     * @param supervisorPositionId supervisor position; ignored for a MyHR center
     * @param pl3Code process
     * @param keys persisted keys
     * @return alignment against the current Delivery HC
     */
    public TimesheetAlignment align(
            String center, String supervisorPositionId, String pl3Code, List<TimesheetAlignment.Key> keys) {
        if (myHrCenter(center)) {
            return myHr.align(center, pl3Code, keys);
        }
        return timesheet.align(center, supervisorPositionId, pl3Code, keys);
    }

    /**
     * @param center toolkit Center
     * @return current org snapshot for that Center's source
     */
    public ActiveSnapshot requireOrg(String center) {
        if (myHrCenter(center)) {
            return myHr.requireOrg();
        }
        return timesheet.activeDaily(center);
    }

    /**
     * @param center toolkit Center
     * @return current Delivery HC snapshot for that Center's source
     */
    public ActiveSnapshot requireKpi(String center) {
        if (myHrCenter(center)) {
            return myHr.requireKpi();
        }
        return timesheet.activeMonthly(center);
    }
}
