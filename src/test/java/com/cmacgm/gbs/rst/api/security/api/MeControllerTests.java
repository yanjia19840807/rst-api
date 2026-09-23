package com.cmacgm.gbs.rst.api.security.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.mail.application.SsoProfileService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.dev.DevIdentityProperties;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MeControllerTests {

    @Test
    void includesJobRoleFromActiveTimesheetPerson() {
        SsoProfileService profiles = mock(SsoProfileService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DevIdentityProperties> devIdentity = mock(ObjectProvider.class);
        TimesheetReadService timesheet = mock(TimesheetReadService.class);
        when(devIdentity.getIfAvailable()).thenReturn(null);
        when(timesheet.findActiveJobRole("S00000001")).thenReturn("Billing Clerk");

        MeController controller = new MeController(profiles, devIdentity, timesheet);
        RstPrincipal principal = new RstPrincipal(
                "S00000001",
                "Agent One",
                "s00000001@dev.local",
                Set.of("AGENT"),
                Set.of("TIMESHEET"),
                "GBS INDIA");

        assertThat(controller.me(principal).jobRole()).isEqualTo("Billing Clerk");
    }

    @Test
    void leavesJobRoleEmptyWhenPersonIsMissing() {
        SsoProfileService profiles = mock(SsoProfileService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DevIdentityProperties> devIdentity = mock(ObjectProvider.class);
        TimesheetReadService timesheet = mock(TimesheetReadService.class);
        when(devIdentity.getIfAvailable()).thenReturn(null);
        when(timesheet.findActiveJobRole("ADMIN001")).thenReturn(null);

        MeController controller = new MeController(profiles, devIdentity, timesheet);
        RstPrincipal principal = new RstPrincipal(
                "ADMIN001", "Admin", "admin@dev.local", Set.of("ADMIN"), Set.of(), null);

        assertThat(controller.me(principal).jobRole()).isNull();
    }

    @Test
    void positionCoverageKeepsTheCallerAndReportsTheCoveredPosition() {
        SsoProfileService profiles = mock(SsoProfileService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DevIdentityProperties> devIdentity = mock(ObjectProvider.class);
        TimesheetReadService timesheet = mock(TimesheetReadService.class);
        when(devIdentity.getIfAvailable()).thenReturn(null);
        when(timesheet.findActiveJobRole("S00813982")).thenReturn("Supervisor");
        when(timesheet.roleTypesOfPosition("174327")).thenReturn(List.of("AGENT"));
        when(timesheet.occupant("174327"))
                .thenReturn(new TimesheetReadService.Occupant("174327", "S00580242", "WU Rongchan"));
        UUID delegationId = UUID.randomUUID();

        MeController controller = new MeController(profiles, devIdentity, timesheet);
        RstPrincipal principal = new RstPrincipal(
                "S00813982",
                "CHEN Cindy",
                "s00813982@dev.local",
                Set.of("SUPERVISOR", "AGENT"),
                Set.of("SELF"),
                "GBS CHINA",
                "S00813982",
                "CHEN Cindy",
                delegationId,
                "174327");

        var me = controller.me(principal);
        assertThat(me.ccgid()).isEqualTo("S00813982");
        assertThat(me.displayName()).isEqualTo("CHEN Cindy");
        assertThat(me.delegatedPositionId()).isEqualTo("174327");
        assertThat(me.delegatedPositionRoles()).containsExactly("AGENT");
        assertThat(me.delegatedOccupantName()).isEqualTo("WU Rongchan");
    }
}
