package com.cmacgm.gbs.rst.api.audit.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.cmacgm.gbs.rst.api.security.Handler;
import org.junit.jupiter.api.Test;

class AuditActorViewTests {

    @Test
    void fromHandlerShowsSubjectViaActor() {
        Handler handler = new Handler("SUP1", "Yang Brenda", "AGT1", "Li Wei");

        AuditActorView view = AuditActorView.from(handler);

        assertThat(view.delegated()).isTrue();
        assertThat(view.subjectName()).isEqualTo("Yang Brenda");
        assertThat(view.actorName()).isEqualTo("Li Wei");
        assertThat(view.displayName()).isEqualTo("Yang Brenda via Li Wei");
    }

    @Test
    void selfShowsSubjectOnly() {
        assertThat(AuditActorView.self("SUP1", "Yang Brenda").displayName())
                .isEqualTo("Yang Brenda");
    }
}
