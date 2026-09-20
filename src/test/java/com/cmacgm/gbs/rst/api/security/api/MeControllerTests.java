package com.cmacgm.gbs.rst.api.security.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.mail.application.SsoProfileService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.dev.DevIdentityProperties;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
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
        when(timesheet.findActivePerson("S00000001"))
                .thenReturn(Optional.of(TimesheetPerson.create(
                        UUID.randomUUID(),
                        "S00000001",
                        "EMP-1",
                        "GBS INDIA",
                        "Agent One",
                        "s00000001@dev.local",
                        "EMP-POS-1",
                        "Billing Clerk")));

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
        when(timesheet.findActivePerson("ADMIN001")).thenReturn(Optional.empty());

        MeController controller = new MeController(profiles, devIdentity, timesheet);
        RstPrincipal principal = new RstPrincipal(
                "ADMIN001", "Admin", "admin@dev.local", Set.of("ADMIN"), Set.of(), null);

        assertThat(controller.me(principal).jobRole()).isNull();
    }
}
