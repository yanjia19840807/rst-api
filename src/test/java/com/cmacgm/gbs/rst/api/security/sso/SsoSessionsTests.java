package com.cmacgm.gbs.rst.api.security.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import com.cmacgm.gbs.rst.api.security.RstAuthenticationToken;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.RstRoles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

class SsoSessionsTests {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void beginLoginStoresStateNonceAndSafeReturnPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String state = SsoSessions.beginLogin(request, "/supervisor/toolkits");

        assertThat(state).isNotBlank();
        assertThat(request.getSession(false).getAttribute(SsoSessions.STATE)).isEqualTo(state);
        assertThat(request.getSession(false).getAttribute(SsoSessions.NONCE)).isInstanceOf(String.class);
        assertThat(request.getSession(false).getAttribute(SsoSessions.RETURN_PATH))
                .isEqualTo("/supervisor/toolkits");
        assertThat(SsoSessions.requireNonce(request, state))
                .isEqualTo(request.getSession(false).getAttribute(SsoSessions.NONCE));
    }

    @Test
    void rejectsOpenRedirectAndMismatchedState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String state = SsoSessions.beginLogin(request, "https://evil.example/phish");
        assertThat(SsoSessions.takeReturnPath(request)).isEqualTo("/");

        SsoSessions.beginLogin(request, "//evil.example");
        assertThatThrownBy(() -> SsoSessions.requireNonce(request, state))
                .isInstanceOf(SsoException.class)
                .extracting(ex -> ((SsoException) ex).code())
                .isEqualTo("sso-state-invalid");
    }

    @Test
    void establishWritesPrincipalAndSecurityContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RstPrincipal principal = new RstPrincipal(
                "S001",
                "Ada",
                "ada@cma-cgm.com",
                Set.of(RstRoles.GOVERNANCE),
                Set.of(),
                null);

        SsoSessions.establish(request, principal);

        assertThat(SsoSessions.current(request)).isEqualTo(principal);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(RstAuthenticationToken.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_GOVERNANCE");
    }

    @Test
    void clearInvalidatesSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SsoSessions.beginLogin(request, "/");
        SsoSessions.clear(request);

        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
