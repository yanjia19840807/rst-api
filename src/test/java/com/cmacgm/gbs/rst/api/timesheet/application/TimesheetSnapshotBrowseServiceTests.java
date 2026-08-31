package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetAssignment;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPosition;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetAssignmentRepository;
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
    private TimesheetAssignmentRepository assignments;
    private TimesheetKpiRepository kpis;
    private TimesheetSnapshotBrowseService service;

    @BeforeEach
    void setUp() {
        people = mock(TimesheetPersonRepository.class);
        positions = mock(TimesheetPositionRepository.class);
        scopes = mock(TimesheetScopeRepository.class);
        assignments = mock(TimesheetAssignmentRepository.class);
        kpis = mock(TimesheetKpiRepository.class);
        service = new TimesheetSnapshotBrowseService(people, positions, scopes, assignments, kpis);
    }

    @Test
    void mapsBlankFiltersAndPersonRows() {
        UUID runId = UUID.randomUUID();
        when(people.searchActive(eq(""), eq("anna"), any()))
                .thenReturn(new PageImpl<>(
                        List.of(TimesheetPerson.create(
                                runId, "S00000001", "EMP-1", "GBS CHINA", "TIAN Anna", "a@cma-cgm.com", "748595")),
                        PageRequest.of(0, 10),
                        1));

        var page = service.people(null, "anna", 1, 10);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items())
                .extracting(
                        TimesheetSnapshotBrowseService.PersonView::ccgid,
                        TimesheetSnapshotBrowseService.PersonView::center,
                        TimesheetSnapshotBrowseService.PersonView::positionId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("S00000001", "GBS CHINA", "748595"));
    }

    @Test
    void mapsMonthlyRows() {
        UUID runId = UUID.randomUUID();
        when(scopes.searchActive(eq("GBS CHINA"), eq(""), eq(""), any()))
                .thenReturn(new PageImpl<>(
                        List.of(TimesheetScope.create(
                                runId, "POS-SUP-1", "PL3", "GBS CHINA", "PL3 Name", "Finance", "PL1", "PL2")),
                        PageRequest.of(0, 10),
                        1));
        when(assignments.searchActive(eq(""), eq("PL3"), eq(""), any()))
                .thenReturn(new PageImpl<>(
                        List.of(TimesheetAssignment.create(runId, "S00000001", "EMP-1", "POS-SUP-1", "PL3")),
                        PageRequest.of(0, 10),
                        1));
        when(kpis.searchActive(eq(""), eq(""), eq("MY"), any()))
                .thenReturn(new PageImpl<>(
                        List.of(TimesheetKpi.create(
                                runId, "POS-SUP-1", "PL3", "CMA", "Site A", "MY", new BigDecimal("1.5"))),
                        PageRequest.of(0, 10),
                        1));

        assertThat(service.scopes("GBS CHINA", null, null, 1, 10).items())
                .extracting(TimesheetSnapshotBrowseService.ScopeView::pl3Code)
                .containsExactly("PL3");
        assertThat(service.assignments(null, "PL3", null, 1, 10).items())
                .extracting(TimesheetSnapshotBrowseService.AssignmentView::empCcgid)
                .containsExactly("S00000001");
        assertThat(service.kpis(null, null, "MY", 1, 10).items())
                .extracting(TimesheetSnapshotBrowseService.KpiView::hc)
                .containsExactly(new BigDecimal("1.5"));
    }
}
