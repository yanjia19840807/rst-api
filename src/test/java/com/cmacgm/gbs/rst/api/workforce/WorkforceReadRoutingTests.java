package com.cmacgm.gbs.rst.api.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.sourcedata.application.CenterSourceService;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterDeliveryHc;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterSource;
import com.cmacgm.gbs.rst.api.sourcedata.persistence.CenterDeliveryHcRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.ProductSeat;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPositionParent;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgSeat;
import com.cmacgm.gbs.rst.api.timesheet.domain.ProcessScope;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionParentRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgSeatRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.ProcessScopeRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SourceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkforceReadRoutingTests {

    private static final UUID SNAPSHOT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private SourceSnapshotRepository snapshots;
    @Mock
    private OrgPersonRepository people;
    @Mock
    private OrgSeatRepository seats;
    @Mock
    private OrgPositionRepository positions;
    @Mock
    private OrgPositionParentRepository parents;
    @Mock
    private ProcessScopeRepository processes;
    @Mock
    private CenterDeliveryHcRepository deliveryHc;
    @Mock
    private CenterSourceService centerSources;
    @Mock
    private TimesheetReadService timesheet;

    private MyHrWorkforceSource myHr;
    private WorkforceRead workforce;

    @BeforeEach
    void setUp() {
        myHr = new MyHrWorkforceSource(
                snapshots, people, seats, positions, parents, processes, deliveryHc, centerSources);
        workforce = new WorkforceRead(timesheet, myHr, centerSources);
        lenient().when(people.findActiveMyHrByCcgid(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void supervisorHierarchyIsEveryCurrentSkpiProcessOfTheCenter() {
        when(centerSources.sourceMode("GBS INDIA")).thenReturn(CenterSource.MYHR_SKPI);
        when(people.findActiveMyHrByCcgid("SUP001")).thenReturn(Optional.of(person("SUP001")));
        when(seats.findActiveMyHrPositionIdsByCcgidAndRole("SUP001", "SUPERVISOR"))
                .thenReturn(List.of("POS-SUP"));
        when(processes.findActiveSharedByCenter("GBS INDIA")).thenReturn(List.of(
                ProcessScope.create(SNAPSHOT, "GBS INDIA", "PL3-A", "Process A", "Finance", "L1", "L2"),
                ProcessScope.create(SNAPSHOT, "GBS INDIA", "PL3-B", "Process B", "Finance", "L1", "L2")));

        var rows = myHr.supervisorHierarchy("SUP001");

        assertThat(rows).extracting(row -> row.pl3Code()).containsExactly("PL3-A", "PL3-B");
        assertThat(rows).allMatch(row -> "POS-SUP".equals(row.supervisorPositionId()));
        assertThat(rows).allMatch(row -> "GBS INDIA".equals(row.center()));
    }

    @Test
    void kpisUseCenterDeliveryHcForTheSelectedCountries() {
        when(deliveryHc.findActiveByCenterAndPl3("GBS INDIA", "PL3-A")).thenReturn(List.of(
                CenterDeliveryHc.create(SNAPSHOT, "GBS INDIA", "PL3-A", "CMA", "INMAA", "IN", new BigDecimal("4")),
                CenterDeliveryHc.create(SNAPSHOT, "GBS INDIA", "PL3-A", "CMA", "INBOM", "FR", new BigDecimal("6"))));

        var rows = myHr.kpis("GBS INDIA", "PL3-A", List.of("IN"));

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().customerCountry()).isEqualTo("IN");
        assertThat(rows.getFirst().deliveryHc()).isEqualByComparingTo("4");
    }

    @Test
    void agentCanUseRequiresTheSeatParentAndACurrentProcess() {
        when(centerSources.sourceMode("GBS INDIA")).thenReturn(CenterSource.MYHR_SKPI);
        when(people.findActiveMyHrByCcgid("AGT001")).thenReturn(Optional.of(person("AGT001")));
        when(processes.existsActiveShared("GBS INDIA", "PL3-A")).thenReturn(true);
        when(seats.findActiveMyHrByCcgidIn(List.of("AGT001"))).thenReturn(List.of(
                OrgSeat.create(SNAPSHOT, "AGT001", "POS-AGT", "AGENT")));
        when(parents.findMyHrByPositionId("POS-AGT")).thenReturn(List.of(
                OrgPositionParent.create(SNAPSHOT, "POS-AGT", "AGENT", "POS-SUP", "SUPERVISOR")));

        assertThat(myHr.agentCanUse("AGT001", "POS-SUP", "PL3-A", "GBS INDIA")).isTrue();
        assertThat(myHr.agentCanUse("AGT001", "POS-OTHER", "PL3-A", "GBS INDIA")).isFalse();
    }

    @Test
    void leftoverDailySeatInAMyHrCenterIsNotALoginSeat() {
        when(timesheet.findActiveProductSeat("IND001")).thenReturn(Optional.of(
                new ProductSeat(Set.of("AGENT"), "GBS INDIA", "Left Over", "left@dev.local")));
        when(centerSources.sourceMode("GBS INDIA")).thenReturn(CenterSource.MYHR_SKPI);

        assertThat(workforce.findActiveProductSeat("IND001")).isEmpty();
    }

    @Test
    void dailySeatInATimesheetCenterStaysAvailable() {
        when(timesheet.findActiveProductSeat("CHN001")).thenReturn(Optional.of(
                new ProductSeat(Set.of("AGENT"), "GBS CHINA", "Daily Agent", "daily@dev.local")));
        when(centerSources.sourceMode("GBS CHINA")).thenReturn(CenterSource.TIMESHEET);

        assertThat(workforce.findActiveProductSeat("CHN001")).isPresent();
    }

    private static OrgPerson person(String ccgid) {
        return OrgPerson.create(SNAPSHOT, ccgid, "E1", "GBS INDIA", ccgid, ccgid + "@dev.local", "Agent");
    }
}
