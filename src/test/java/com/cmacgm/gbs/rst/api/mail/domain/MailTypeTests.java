package com.cmacgm.gbs.rst.api.mail.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MailTypeTests {

    @Test
    void supervisorOwnsOutcomeMailsOnly() {
        assertThat(MailType.forRole("SUPERVISOR")).containsExactly(MailType.SUBMISSION_OUTCOME);
    }

    @Test
    void managerAndCdhOwnAwaitingApproval() {
        assertThat(MailType.forRole("MANAGER")).containsExactly(MailType.APPROVAL_REQUESTED);
        assertThat(MailType.forRole("CDH")).containsExactly(MailType.APPROVAL_REQUESTED);
    }

    @Test
    void lthOwnsAwaitingApprovalAndSyncFailed() {
        assertThat(MailType.forRole("LTH")).containsExactly(
                MailType.APPROVAL_REQUESTED, MailType.TIMESHEET_SYNC_FAILED);
    }

    @Test
    void adminOwnsSyncFailedOnly() {
        assertThat(MailType.forRole("ADMIN")).containsExactly(MailType.TIMESHEET_SYNC_FAILED);
    }

    @Test
    void agentAndHoHaveNoMailTypes() {
        assertThat(MailType.forRole("AGENT")).isEmpty();
        assertThat(MailType.forRole("HO")).isEmpty();
        assertThat(MailType.forRole(null)).isEmpty();
    }

    @Test
    void mailRolePicksTheSingleProductRole() {
        assertThat(MailType.mailRole(List.of("SUPERVISOR"))).isEqualTo("SUPERVISOR");
        assertThat(MailType.mailRole(Set.of("lth"))).isEqualTo("LTH");
        assertThat(MailType.mailRole(Set.of("admin"))).isEqualTo("ADMIN");
        assertThat(MailType.mailRole(List.of("AGENT", "HO"))).isNull();
    }

    @Test
    void fromIdAcceptsSlugOrEnumName() {
        assertThat(MailType.fromId("approval.requested")).isEqualTo(MailType.APPROVAL_REQUESTED);
        assertThat(MailType.fromId("TIMESHEET_SYNC_FAILED")).isEqualTo(MailType.TIMESHEET_SYNC_FAILED);
        assertThat(MailType.fromId("submission.returned")).isEqualTo(MailType.SUBMISSION_OUTCOME);
        assertThat(MailType.fromId("unknown")).isNull();
    }
}
