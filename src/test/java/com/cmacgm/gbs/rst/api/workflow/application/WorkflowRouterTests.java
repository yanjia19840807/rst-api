package com.cmacgm.gbs.rst.api.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import com.cmacgm.gbs.rst.api.delegation.persistence.DelegationRepository;
import com.cmacgm.gbs.rst.api.domainhead.application.DomainHeadConfigService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.RstRoles;
import com.cmacgm.gbs.rst.api.workforce.WorkforceRead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowRouterTests {

    @Mock
    private WorkforceRead workforce;

    @Mock
    private DomainHeadConfigService domainHeads;

    @Mock
    private DelegationRepository delegations;

    private WorkflowRouter router;

    @BeforeEach
    void setUp() {
        router = new WorkflowRouter(workforce, domainHeads, delegations);
    }

    @Test
    void domainHeadQueueIncludesCenterRolesCcgidAssignment() {
        when(workforce.positionsForRole("S00683842", "DOMAIN_HEAD")).thenReturn(List.of());
        when(domainHeads.assignedKeysFor("S00683842")).thenReturn(Set.of("S00683842"));
        when(domainHeads.assignedLthKeysFor("S00683842")).thenReturn(Set.of());

        Set<String> positions = router.positionsFor(principal("S00683842", Set.of(RstRoles.DOMAIN_HEAD)));

        assertThat(positions).contains("S00683842");
    }

    @Test
    void domainHeadQueueIncludesCcgidWhenRoleIsForcedWithoutTimesheetSeat() {
        when(workforce.positionsForRole("S00683842", "DOMAIN_HEAD")).thenReturn(List.of());
        when(domainHeads.assignedKeysFor("S00683842")).thenReturn(Set.of());
        when(domainHeads.assignedLthKeysFor("S00683842")).thenReturn(Set.of());

        Set<String> positions = router.positionsFor(principal("S00683842", Set.of(RstRoles.DOMAIN_HEAD)));

        assertThat(positions).contains("S00683842");
    }

    @Test
    void managerQueueDoesNotPickUpUnrelatedCdhKeys() {
        when(workforce.positionsForRole("S00628202", "SR_MANAGER")).thenReturn(List.of("174210"));
        when(domainHeads.assignedKeysFor("S00628202")).thenReturn(Set.of());
        when(domainHeads.assignedLthKeysFor("S00628202")).thenReturn(Set.of());

        Set<String> positions = router.positionsFor(principal("S00628202", Set.of(RstRoles.SR_MANAGER)));

        assertThat(positions).containsExactly("174210");
    }

    @Test
    void managerPreviewListsEveryConfiguredDomainHead() {
        when(domainHeads.liveCdhs("GBS CHINA", "FINANCE")).thenReturn(List.of(
                new WorkflowRouter.RoutedStep("POS-1", "DH001", "Ada"),
                new WorkflowRouter.RoutedStep("POS-2", "DH002", "Ben")));

        WorkflowRouter.NextHop next = router.previewNext("SR_MANAGER", "SUP-1", "GBS CHINA", "FINANCE");

        assertThat(next.positionId()).isNull();
        assertThat(next.handlers())
                .extracting(WorkflowRouter.NextHop.HandlerOption::positionId)
                .containsExactly("POS-1", "POS-2");
    }

    @Test
    void managerPreviewKeepsTheOnlyDomainHeadSelected() {
        when(domainHeads.liveCdhs("GBS CHINA", "FINANCE")).thenReturn(List.of(
                new WorkflowRouter.RoutedStep("POS-1", "DH001", "Ada")));

        WorkflowRouter.NextHop next = router.previewNext("SR_MANAGER", "SUP-1", "GBS CHINA", "FINANCE");

        assertThat(next.positionId()).isEqualTo("POS-1");
        assertThat(next.reviewerName()).isEqualTo("Ada");
        assertThat(next.handlers()).hasSize(1);
    }

    @Test
    void lthQueueIncludesSentinelWhenCenterRolesAssignsLthWithoutRoleClaim() {
        when(domainHeads.assignedKeysFor("S00690001")).thenReturn(Set.of());
        when(domainHeads.assignedLthKeysFor("S00690001")).thenReturn(Set.of("POS-LTH-1"));

        Set<String> positions = router.positionsFor(principal("S00690001", Set.of()));

        assertThat(positions).containsExactly(RstRoles.LOCAL_TRANSFORMATION_HEAD);
    }

    private static RstPrincipal principal(String ccgid, Set<String> roles) {
        return new RstPrincipal(ccgid, ccgid, ccgid + "@dev.local", roles, Set.of(), "GBS CHINA");
    }
}
