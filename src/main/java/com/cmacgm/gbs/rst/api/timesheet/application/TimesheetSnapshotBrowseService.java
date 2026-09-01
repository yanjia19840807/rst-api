package com.cmacgm.gbs.rst.api.timesheet.application;

import java.math.BigDecimal;
import java.util.List;

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
     * @param q name / CCGID / emp id / email
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
     * @param q any of the four position ids
     * @param page 1-based page
     * @param pageSize page size
     * @return one row per AGENT position with the parent chain
     */
    @Transactional(readOnly = true)
    public PageResponse<PositionView> positions(String q, int page, int pageSize) {
        return PageResponse.from(positions.searchActiveChains(blank(q), pageOf(page, pageSize)), this::toPosition);
    }

    /**
     * @param center exact center
     * @param domain exact domain
     * @param q PL3 / supervisor / PL1 / PL2
     * @param page 1-based page
     * @param pageSize page size
     * @return scopes page
     */
    @Transactional(readOnly = true)
    public PageResponse<ScopeView> scopes(String center, String domain, String q, int page, int pageSize) {
        return PageResponse.from(
                scopes.searchActive(blank(center), blank(domain), blank(q), pageOf(page, pageSize)), this::toScope);
    }

    /**
     * @param supervisorPositionId exact supervisor position
     * @param pl3Code exact PL3
     * @param q CCGID / emp id
     * @param page 1-based page
     * @param pageSize page size
     * @return assignments page
     */
    @Transactional(readOnly = true)
    public PageResponse<AssignmentView> assignments(
            String supervisorPositionId, String pl3Code, String q, int page, int pageSize) {
        return PageResponse.from(
                assignments.searchActive(
                        blank(supervisorPositionId), blank(pl3Code), blank(q), pageOf(page, pageSize)),
                this::toAssignment);
    }

    /**
     * @param supervisorPositionId exact supervisor position
     * @param pl3Code exact PL3
     * @param q carrier / site / country
     * @param page 1-based page
     * @param pageSize page size
     * @return Delivery HC page
     */
    @Transactional(readOnly = true)
    public PageResponse<KpiView> kpis(String supervisorPositionId, String pl3Code, String q, int page, int pageSize) {
        return PageResponse.from(
                kpis.searchActive(blank(supervisorPositionId), blank(pl3Code), blank(q), pageOf(page, pageSize)),
                this::toKpi);
    }

    private PersonView toPerson(TimesheetPerson row) {
        return new PersonView(
                row.getCcgid(), row.getEmpId(), row.getName(), row.getEmail(), row.getCenter(), row.getPositionId());
    }

    private PositionView toPosition(TimesheetPositionRepository.PositionChain row) {
        return new PositionView(
                row.getAgentPositionId(),
                row.getSupervisorPositionId(),
                row.getSrManagerPositionId(),
                row.getDomainHeadPositionId());
    }

    private ScopeView toScope(TimesheetScope row) {
        return new ScopeView(
                row.getSupervisorPositionId(),
                row.getCenter(),
                row.getDomain(),
                row.getPl1(),
                row.getPl2(),
                row.getPl3Code(),
                row.getPl3Name());
    }

    private AssignmentView toAssignment(TimesheetAssignment row) {
        return new AssignmentView(
                row.getEmpCcgid(), row.getEmpId(), row.getSupervisorPositionId(), row.getPl3Code());
    }

    private KpiView toKpi(TimesheetKpi row) {
        return new KpiView(
                row.getSupervisorPositionId(),
                row.getPl3Code(),
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
            String supervisorPositionId,
            String srManagerPositionId,
            String domainHeadPositionId) {
    }

    /**
     * Monthly scope row.
     */
    public record ScopeView(
            String supervisorPositionId,
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
    public record AssignmentView(String empCcgid, String empId, String supervisorPositionId, String pl3Code) {
    }

    /**
     * Monthly Delivery HC row.
     */
    public record KpiView(
            String supervisorPositionId,
            String pl3Code,
            String carrier,
            String site,
            String customerCountry,
            BigDecimal hc) {
    }
}
