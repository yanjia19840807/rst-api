package com.cmacgm.gbs.rst.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HandlerTests {

    @Test
    void selfHasNoActorAndShowsSubjectName() {
        Handler handler = Handler.self("SUP1", "Yang Brenda");

        assertThat(handler.hasActor()).isFalse();
        assertThat(handler.displayName()).isEqualTo("Yang Brenda");
    }

    @Test
    void delegatedShowsActorOnBehalfOfSubject() {
        Handler handler = new Handler("SUP1", "Yang Brenda", "AGT1", "Li Wei");

        assertThat(handler.hasActor()).isTrue();
        assertThat(handler.displayName()).isEqualTo("Li Wei (on behalf of Yang Brenda)");
    }

    @Test
    void fromPrincipalCapturesDelegate() {
        RstPrincipal principal = new RstPrincipal(
                "SUP1",
                "Yang Brenda",
                "a@b.c",
                Set.of("SUPERVISOR"),
                Set.of(),
                "Kuala Lumpur",
                "AGT1",
                "Li Wei",
                UUID.randomUUID());

        Handler handler = Handler.from(principal);

        assertThat(handler.hasActor()).isTrue();
        assertThat(handler.subjectCcgid()).isEqualTo("SUP1");
        assertThat(handler.actorCcgid()).isEqualTo("AGT1");
        assertThat(handler.displayName()).isEqualTo("Li Wei (on behalf of Yang Brenda)");
    }
}
