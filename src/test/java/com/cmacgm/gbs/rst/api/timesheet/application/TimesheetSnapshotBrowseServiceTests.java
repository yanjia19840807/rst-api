package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.SupervisorDeliveryHc;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgSeat;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPositionParent;
import com.cmacgm.gbs.rst.api.timesheet.domain.MonthlyScope;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SupervisorDeliveryHcRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgSeatRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionParentRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.MonthlyScopeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class TimesheetSnapshotBrowseServiceTests {

    private OrgPersonRepository people;
    private OrgSeatRepository seats;
    private OrgPositionRepository positions;
    private OrgPositionParentRepository parents;
    private MonthlyScopeRepository scopes;
    private SupervisorDeliveryHcRepository kpis;
    private TimesheetSnapshotBrowseService service;

    @BeforeEach
    void setUp() {
        people = mock(OrgPersonRepository.class);
        seats = mock(OrgSeatRepository.class);
        positions = mock(OrgPositionRepository.class);
        parents = mock(OrgPositionParentRepository.class);
        scopes = mock(MonthlyScopeRepository.class);
        kpis = mock(SupervisorDeliveryHcRepository.class);
        service = new TimesheetSnapshotBrowseService(people, seats, positions, parents, scopes, kpis);
    }

    @Test
    void mapsBlankFiltersAndPersonRows() {
        UUID runId = UUID.randomUUID();
        when(people.searchActive(eq(""), eq("anna"), any()))
                .thenReturn(new PageImpl<>(
                        List.of(OrgPerson.create(
                                runId,
                                "S00000001",
                                "EMP-1",
                                "GBS INDIA",
                                "TIAN Anna",
                                "a@cma-cgm.com",
                                "Billing Clerk")),
                        PageRequest.of(0, 10),
                        1));
        var page = service.people(null, "anna", 1, 10);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items())
                .extracting(
                        TimesheetSnapshotBrowseService.PersonView::ccgid,
                        TimesheetSnapshotBrowseService.PersonView::center,
                        TimesheetSnapshotBrowseService.PersonView::name,
                        TimesheetSnapshotBrowseService.PersonView::jobRole)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "S00000001", "GBS INDIA", "TIAN Anna", "Billing Clerk"));
    }

    @Test
    void mapsPositionNodes() {
        when(positions.searchActiveNodes(eq("GBS INDIA"), eq("174"), any()))
                .thenReturn(new PageImpl<>(
                        List.of(
                                node("174050", "SUPERVISOR", "GBS INDIA"),
                                node("174050", "SR_MANAGER", "GBS INDIA")),
                        PageRequest.of(0, 10),
                        2));

        UUID runId = UUID.randomUUID();
        when(parents.findActiveByPositionIdIn(eq("GBS INDIA"), any()))
                .thenReturn(List.of(OrgPositionParent.create(
                        runId, "174050", "SUPERVISOR", "173171", "SR_MANAGER")));
        stubOccupants(
                runId,
                OrgSeat.create(runId, "S00000002", "174050", "SUPERVISOR"),
                OrgSeat.create(runId, "S00000002", "174050", "SR_MANAGER"));

        assertThat(service.positions("GBS INDIA", "174", 1, 10).items())
                .extracting(
                        TimesheetSnapshotBrowseService.PositionView::positionId,
                        TimesheetSnapshotBrowseService.PositionView::roleType,
                        TimesheetSnapshotBrowseService.PositionView::parentPositionId,
                        TimesheetSnapshotBrowseService.PositionView::occupantName,
                        TimesheetSnapshotBrowseService.PositionView::center)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "174050", "SUPERVISOR", "173171", "TANG Lavender", "GBS INDIA"),
                        org.assertj.core.groups.Tuple.tuple(
                                "174050", "SR_MANAGER", null, "TANG Lavender", "GBS INDIA"));
    }

    @Test
    void mapsOccupancies() {
        when(seats.searchActive(eq(""), eq("donna"), any()))
                .thenReturn(new PageImpl<>(
                        List.of(
                                occupancy("S00000002", "Donna", "174050", "SUPERVISOR", "GBS INDIA"),
                                occupancy("S00000002", "Donna", "174050", "SR_MANAGER", "GBS INDIA")),
                        PageRequest.of(0, 10),
                        2));

        assertThat(service.occupancies(null, "donna", 1, 10).items())
                .extracting(
                        TimesheetSnapshotBrowseService.OccupancyView::ccgid,
                        TimesheetSnapshotBrowseService.OccupancyView::positionId,
                        TimesheetSnapshotBrowseService.OccupancyView::roleType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("S00000002", "174050", "SUPERVISOR"),
                        org.assertj.core.groups.Tuple.tuple("S00000002", "174050", "SR_MANAGER"));
    }

    @Test
    void mapsMonthlyRows() {
        UUID runId = UUID.randomUUID();
        when(scopes.searchActive(eq("GBS INDIA"), eq(""), eq(""), any()))
                .thenReturn(new PageImpl<>(
                        List.of(MonthlyScope.create(
                                runId, "POS-SUP-1", "PL3", "GBS INDIA", "PL3 Name", "Finance", "PL1", "PL2")),
                        PageRequest.of(0, 10),
                        1));
        when(positions.searchActiveAssignments(eq("GBS INDIA"), eq(""), eq("POS-SUP-1"), eq("PL3"), any()))
                .thenReturn(new PageImpl<>(
                        List.of(derived("172545", "POS-SUP-1", "PL3", "PL3 Name", "GBS INDIA")),
                        PageRequest.of(0, 10),
                        1));
        when(scopes.findActiveBySupervisorPositionIdIn(any()))
                .thenReturn(List.of(MonthlyScope.create(
                        runId, "POS-SUP-1", "PL3", "GBS INDIA", "PL3 Name", "Finance", "PL1", "PL2")));
        when(kpis.searchActive(eq("GBS INDIA"), eq(""), eq(""), any()))
                .thenReturn(new PageImpl<>(
                        List.of(SupervisorDeliveryHc.create(
                                runId, "POS-SUP-1", "PL3", "GBS INDIA", "CMA", "Site A", "MY", new BigDecimal("1.5"))),
                        PageRequest.of(0, 10),
                        1));
        stubOccupants(
                runId,
                OrgSeat.create(runId, "S00000001", "172545", "AGENT"),
                OrgSeat.create(runId, "S00000002", "POS-SUP-1", "SUPERVISOR"));

        assertThat(service.scopes("GBS INDIA", null, null, 1, 10).items())
                .extracting(
                        TimesheetSnapshotBrowseService.ScopeView::pl3Code,
                        TimesheetSnapshotBrowseService.ScopeView::supervisorName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PL3", "TANG Lavender"));
        assertThat(service.assignments("GBS INDIA", null, "POS-SUP-1", "PL3", 1, 10).items())
                .extracting(
                        TimesheetSnapshotBrowseService.AssignmentView::agentPositionId,
                        TimesheetSnapshotBrowseService.AssignmentView::agentName,
                        TimesheetSnapshotBrowseService.AssignmentView::pl3Name,
                        TimesheetSnapshotBrowseService.AssignmentView::center)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "172545", "TIAN Anna", "PL3 Name", "GBS INDIA"));
        assertThat(service.kpis("GBS INDIA", null, null, 1, 10).items())
                .extracting(
                        TimesheetSnapshotBrowseService.KpiView::supervisorName,
                        TimesheetSnapshotBrowseService.KpiView::center,
                        TimesheetSnapshotBrowseService.KpiView::pl3Name,
                        TimesheetSnapshotBrowseService.KpiView::hc)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "TANG Lavender", "GBS INDIA", "PL3 Name", new BigDecimal("1.5")));
    }

    private void stubOccupants(UUID runId, OrgSeat... occupied) {
        when(seats.findActiveByPositionIdIn(any())).thenReturn(List.of(occupied));
        when(people.findActiveByCcgidIn(any())).thenReturn(List.of(
                OrgPerson.create(
                        runId, "S00000001", "EMP-1", "GBS INDIA", "TIAN Anna", "a@cma-cgm.com", "Billing Clerk"),
                OrgPerson.create(
                        runId, "S00000002", "EMP-2", "GBS INDIA", "TANG Lavender", "b@cma-cgm.com", null)));
    }

    private static OrgPositionRepository.PositionNode node(
            String positionId, String roleType, String center) {
        return new OrgPositionRepository.PositionNode() {
            @Override
            public String getPositionId() {
                return positionId;
            }

            @Override
            public String getRoleType() {
                return roleType;
            }

            @Override
            public String getCenter() {
                return center;
            }
        };
    }

    private static OrgSeatRepository.OccupancyRow occupancy(
            String ccgid, String name, String positionId, String roleType, String center) {
        return new OrgSeatRepository.OccupancyRow() {
            @Override
            public String getCcgid() {
                return ccgid;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getPositionId() {
                return positionId;
            }

            @Override
            public String getRoleType() {
                return roleType;
            }

            @Override
            public String getCenter() {
                return center;
            }
        };
    }

    private static OrgPositionRepository.DerivedAssignment derived(
            String agentPositionId,
            String supervisorPositionId,
            String pl3Code,
            String pl3Name,
            String center) {
        return new OrgPositionRepository.DerivedAssignment() {
            @Override
            public String getAgentPositionId() {
                return agentPositionId;
            }

            @Override
            public String getSupervisorPositionId() {
                return supervisorPositionId;
            }

            @Override
            public String getPl3Code() {
                return pl3Code;
            }

            @Override
            public String getPl3Name() {
                return pl3Name;
            }

            @Override
            public String getCenter() {
                return center;
            }
        };
    }
}
