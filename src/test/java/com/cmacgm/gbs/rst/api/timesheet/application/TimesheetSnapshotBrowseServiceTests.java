package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetKpiRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetScopeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class TimesheetSnapshotBrowseServiceTests {

    private TimesheetPersonRepository people;
    private TimesheetPositionRepository positions;
    private TimesheetScopeRepository scopes;
    private TimesheetKpiRepository kpis;
    private TimesheetSnapshotBrowseService service;

    @BeforeEach
    void setUp() {
        people = mock(TimesheetPersonRepository.class);
        positions = mock(TimesheetPositionRepository.class);
        scopes = mock(TimesheetScopeRepository.class);
        kpis = mock(TimesheetKpiRepository.class);
        service = new TimesheetSnapshotBrowseService(people, positions, scopes, kpis);
    }

    @Test
    void mapsBlankFiltersAndPersonRows() {
        UUID runId = UUID.randomUUID();
        when(people.searchActive(eq(""), eq("anna"), any()))
                .thenReturn(new PageImpl<>(
                        List.of(TimesheetPerson.create(
                                runId, "S00000001", "EMP-1", "GBS CHINA INDIA", "TIAN Anna", "a@cma-cgm.com", "748595")),
                        PageRequest.of(0, 10),
                        1));

        var page = service.people(null, "anna", 1, 10);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items())
                .extracting(
                        TimesheetSnapshotBrowseService.PersonView::ccgid,
                        TimesheetSnapshotBrowseService.PersonView::center,
                        TimesheetSnapshotBrowseService.PersonView::positionId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("S00000001", "GBS CHINA INDIA", "748595"));
    }

    @Test
    void mapsPositionChains() {
        when(positions.searchActiveChains(eq("GBS CHINA INDIA"), eq("174"), any()))
                .thenReturn(new PageImpl<>(
                        List.of(new TimesheetPositionRepository.PositionChain() {
                            @Override
                            public String getAgentPositionId() {
                                return "172545";
                            }

                            @Override
                            public String getSupervisorPositionId() {
                                return "174055";
                            }

                            @Override
                            public String getSrManagerPositionId() {
                                return "174100";
                            }

                            @Override
                            public String getCenter() {
                                return "GBS CHINA INDIA";
                            }
                        }),
                        PageRequest.of(0, 10),
                        1));

        UUID runId = UUID.randomUUID();
        when(people.findActiveByPositionIdIn(any()))
                .thenReturn(List.of(
                        TimesheetPerson.create(
                                runId, "S00000001", "EMP-1", "GBS CHINA INDIA", "TIAN Anna", "a@cma-cgm.com", "172545"),
                        TimesheetPerson.create(
                                runId, "S00000002", "EMP-2", "GBS CHINA INDIA", "TANG Lavender", "b@cma-cgm.com", "174055")));

        assertThat(service.positions("GBS CHINA INDIA", "174", 1, 10).items())
                .extracting(
                        TimesheetSnapshotBrowseService.PositionView::agentPositionId,
                        TimesheetSnapshotBrowseService.PositionView::agentName,
                        TimesheetSnapshotBrowseService.PositionView::supervisorPositionId,
                        TimesheetSnapshotBrowseService.PositionView::supervisorName,
                        TimesheetSnapshotBrowseService.PositionView::srManagerPositionId,
                        TimesheetSnapshotBrowseService.PositionView::srManagerName,
                        TimesheetSnapshotBrowseService.PositionView::center)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "172545",
                        "TIAN Anna",
                        "174055",
                        "TANG Lavender",
                        "174100",
                        null,
                        "GBS CHINA INDIA"));
    }

    @Test
    void mapsMonthlyRows() {
        UUID runId = UUID.randomUUID();
        when(scopes.searchActive(eq("GBS CHINA INDIA"), eq(""), eq(""), any()))
                .thenReturn(new PageImpl<>(
                        List.of(TimesheetScope.create(
                                runId, "POS-SUP-1", "PL3", "GBS CHINA INDIA", "PL3 Name", "Finance", "PL1", "PL2")),
                        PageRequest.of(0, 10),
                        1));
        when(positions.searchActiveAssignments(eq("GBS CHINA INDIA"), eq(""), eq("POS-SUP-1"), eq("PL3"), any()))
                .thenReturn(new PageImpl<>(
                        List.of(derived("172545", "POS-SUP-1", "PL3", "PL3 Name", "GBS CHINA INDIA")),
                        PageRequest.of(0, 10),
                        1));
        when(scopes.findActiveBySupervisorPositionIdIn(any()))
                .thenReturn(List.of(TimesheetScope.create(
                        runId, "POS-SUP-1", "PL3", "GBS CHINA INDIA", "PL3 Name", "Finance", "PL1", "PL2")));
        when(kpis.searchActive(eq("GBS CHINA INDIA"), eq(""), eq(""), any()))
                .thenReturn(new PageImpl<>(
                        List.of(TimesheetKpi.create(
                                runId, "POS-SUP-1", "PL3", "GBS CHINA INDIA", "CMA", "Site A", "MY", new BigDecimal("1.5"))),
                        PageRequest.of(0, 10),
                        1));
        when(people.findActiveByPositionIdIn(any()))
                .thenReturn(List.of(
                        TimesheetPerson.create(
                                runId, "S00000001", "EMP-1", "GBS CHINA INDIA", "TIAN Anna", "a@cma-cgm.com", "172545"),
                        TimesheetPerson.create(
                                runId, "S00000002", "EMP-2", "GBS CHINA INDIA", "TANG Lavender", "b@cma-cgm.com", "POS-SUP-1")));

        assertThat(service.scopes("GBS CHINA INDIA", null, null, 1, 10).items())
                .extracting(
                        TimesheetSnapshotBrowseService.ScopeView::pl3Code,
                        TimesheetSnapshotBrowseService.ScopeView::supervisorName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PL3", "TANG Lavender"));
        assertThat(service.assignments("GBS CHINA INDIA", null, "POS-SUP-1", "PL3", 1, 10).items())
                .extracting(
                        TimesheetSnapshotBrowseService.AssignmentView::agentPositionId,
                        TimesheetSnapshotBrowseService.AssignmentView::agentName,
                        TimesheetSnapshotBrowseService.AssignmentView::pl3Name,
                        TimesheetSnapshotBrowseService.AssignmentView::center)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "172545", "TIAN Anna", "PL3 Name", "GBS CHINA INDIA"));
        assertThat(service.kpis("GBS CHINA INDIA", null, null, 1, 10).items())
                .extracting(
                        TimesheetSnapshotBrowseService.KpiView::supervisorName,
                        TimesheetSnapshotBrowseService.KpiView::center,
                        TimesheetSnapshotBrowseService.KpiView::pl3Name,
                        TimesheetSnapshotBrowseService.KpiView::hc)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "TANG Lavender", "GBS CHINA INDIA", "PL3 Name", new BigDecimal("1.5")));
    }

    private static TimesheetPositionRepository.DerivedAssignment derived(
            String agentPositionId,
            String supervisorPositionId,
            String pl3Code,
            String pl3Name,
            String center) {
        return new TimesheetPositionRepository.DerivedAssignment() {
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
