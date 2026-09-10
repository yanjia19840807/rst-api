package com.cmacgm.gbs.rst.api.mail.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MailTypeTests {

    @Test
    void supervisorOwnsWorkflowMail() {
        assertThat(MailType.forRole("SUPERVISOR")).containsExactly(MailType.WORKFLOW);
    }

    @Test
    void managerAndCdhOwnWorkflowMail() {
        assertThat(MailType.forRole("SR_MANAGER")).containsExactly(MailType.WORKFLOW);
        assertThat(MailType.forRole("DOMAIN_HEAD")).containsExactly(MailType.WORKFLOW);
    }

    @Test
    void lthOwnsWorkflowAndSyncFailed() {
        assertThat(MailType.forRole("LOCAL_TRANSFORMATION_HEAD")).containsExactly(
                MailType.WORKFLOW, MailType.TIMESHEET_SYNC_FAILED);
    }

    @Test
    void adminOwnsSyncFailedOnly() {
        assertThat(MailType.forRole("ADMIN")).containsExactly(MailType.TIMESHEET_SYNC_FAILED);
    }

    @Test
    void agentAndHoHaveNoMailTypes() {
        assertThat(MailType.forRole("AGENT")).isEmpty();
        assertThat(MailType.forRole("GOVERNANCE")).isEmpty();
        assertThat(MailType.forRole(null)).isEmpty();
    }

    @Test
    void mailRolePicksTheSingleProductRole() {
        assertThat(MailType.mailRole(List.of("SUPERVISOR"))).isEqualTo("SUPERVISOR");
        assertThat(MailType.mailRole(Set.of("local_transformation_head")))
                .isEqualTo("LOCAL_TRANSFORMATION_HEAD");
        assertThat(MailType.mailRole(Set.of("admin"))).isEqualTo("ADMIN");
        assertThat(MailType.mailRole(List.of("AGENT", "GOVERNANCE"))).isNull();
    }

    @Test
    void fromIdAcceptsSlugOrEnumName() {
        assertThat(MailType.fromId("approval.requested")).isEqualTo(MailType.WORKFLOW);
        assertThat(MailType.fromId("submission.outcome")).isEqualTo(MailType.WORKFLOW);
        assertThat(MailType.fromId("workflow.notification")).isEqualTo(MailType.WORKFLOW);
        assertThat(MailType.fromId("TIMESHEET_SYNC_FAILED")).isEqualTo(MailType.TIMESHEET_SYNC_FAILED);
        assertThat(MailType.fromId("submission.returned")).isEqualTo(MailType.WORKFLOW);
        assertThat(MailType.fromId("unknown")).isNull();
    }
}
