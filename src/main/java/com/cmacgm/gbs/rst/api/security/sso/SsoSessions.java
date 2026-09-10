package com.cmacgm.gbs.rst.api.security.sso;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import com.cmacgm.gbs.rst.api.security.RstAuthenticationToken;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * HttpSession keys for the Azure authorization-code dance and the signed-in principal.
 */
public final class SsoSessions {

    public static final String STATE = "rst.sso.state";
    public static final String NONCE = "rst.sso.nonce";
    public static final String RETURN_PATH = "rst.sso.return";
    public static final String PRINCIPAL = "rst.sso.principal";

    private SsoSessions() {
    }

    /**
     * @return new session (creates one)
     */
    public static HttpSession session(HttpServletRequest request) {
        return request.getSession(true);
    }

    /**
     * Stores CSRF / nonce values for the upcoming callback.
     *
     * @param request HTTP request
     * @param returnPath optional SPA path
     * @return state
     */
    public static String beginLogin(HttpServletRequest request, String returnPath) {
        HttpSession session = session(request);
        String state = UUID.randomUUID().toString();
        session.setAttribute(STATE, state);
        session.setAttribute(NONCE, UUID.randomUUID().toString());
        session.setAttribute(RETURN_PATH, sanitizeReturnPath(returnPath));
        return state;
    }

    /**
     * @param request HTTP request
     * @return nonce stored at /auth
     */
    public static String requireNonce(HttpServletRequest request, String state) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new SsoException("sso-state-invalid", "Sign-in session expired. Try again.");
        }
        Object expected = session.getAttribute(STATE);
        if (expected == null || !expected.equals(state)) {
            throw new SsoException("sso-state-invalid", "Sign-in state does not match.");
        }
        Object nonce = session.getAttribute(NONCE);
        if (nonce == null) {
            throw new SsoException("sso-nonce-invalid", "Sign-in nonce is missing.");
        }
        return String.valueOf(nonce);
    }

    /**
     * @param request HTTP request
     * @return SPA path to open after login
     */
    public static String takeReturnPath(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return "/";
        }
        Object path = session.getAttribute(RETURN_PATH);
        session.removeAttribute(STATE);
        session.removeAttribute(NONCE);
        session.removeAttribute(RETURN_PATH);
        return path instanceof String value && !value.isBlank() ? value : "/";
    }

    /**
     * Persists the principal and installs it on the current request.
     *
     * @param request HTTP request
     * @param principal signed-in user
     */
    public static void establish(HttpServletRequest request, RstPrincipal principal) {
        HttpSession session = session(request);
        session.setAttribute(PRINCIPAL, principal);
        apply(principal);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());
    }

    /**
     * @param request HTTP request
     * @return stored principal
     */
    public static RstPrincipal current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(PRINCIPAL);
        return value instanceof RstPrincipal principal ? principal : null;
    }

    /**
     * @param principal signed-in user
     */
    public static void apply(RstPrincipal principal) {
        var authorities = principal.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                .toList();
        if (authorities.isEmpty()) {
            authorities = List.of();
        }
        SecurityContextHolder.getContext().setAuthentication(
                new RstAuthenticationToken(principal, "sso", authorities));
    }

    /**
     * Clears the RST session.
     *
     * @param request HTTP request
     */
    public static void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private static String sanitizeReturnPath(String returnPath) {
        if (returnPath == null || returnPath.isBlank() || !returnPath.startsWith("/") || returnPath.startsWith("//")) {
            return "/";
        }
        return returnPath;
    }
}
