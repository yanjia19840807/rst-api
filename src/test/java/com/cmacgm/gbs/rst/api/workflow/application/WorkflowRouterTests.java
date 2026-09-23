package com.cmacgm.gbs.rst.api.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import com.cmacgm.gbs.rst.api.delegation.persistence.DelegationRepository;
import com.cmacgm.gbs.rst.api.domainhead.application.DomainHeadConfigService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.RstRoles;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowRouterTests {

    @Mock
    private TimesheetReadService timesheet;

    @Mock
    private DomainHeadConfigService domainHeads;

    @Mock
    private DelegationRepository delegations;

    private WorkflowRouter router;

    @BeforeEach
    void setUp() {
        router = new WorkflowRouter(timesheet, domainHeads, delegations);
    }

    @Test
    void domainHeadQueueIncludesCenterRolesCcgidAssignment() {
        when(timesheet.positionsForRole("S00683842", "DOMAIN_HEAD")).thenReturn(List.of());
        when(domainHeads.assignedKeysFor("S00683842")).thenReturn(Set.of("S00683842"));

        Set<String> positions = router.positionsFor(principal("S00683842", Set.of(RstRoles.DOMAIN_HEAD)));

        assertThat(positions).contains("S00683842");
    }

    @Test
    void domainHeadQueueIncludesCcgidWhenRoleIsForcedWithoutTimesheetSeat() {
        when(timesheet.positionsForRole("S00683842", "DOMAIN_HEAD")).thenReturn(List.of());
        when(domainHeads.assignedKeysFor("S00683842")).thenReturn(Set.of());

        Set<String> positions = router.positionsFor(principal("S00683842", Set.of(RstRoles.DOMAIN_HEAD)));

        assertThat(positions).contains("S00683842");
    }

    @Test
    void managerQueueDoesNotPickUpUnrelatedCdhKeys() {
        when(timesheet.positionsForRole("S00628202", "SR_MANAGER")).thenReturn(List.of("174210"));
        when(domainHeads.assignedKeysFor("S00628202")).thenReturn(Set.of());

        Set<String> positions = router.positionsFor(principal("S00628202", Set.of(RstRoles.SR_MANAGER)));

        assertThat(positions).containsExactly("174210");
    }

    private static RstPrincipal principal(String ccgid, Set<String> roles) {
        return new RstPrincipal(ccgid, ccgid, ccgid + "@dev.local", roles, Set.of(), "GBS CHINA");
    }
}
