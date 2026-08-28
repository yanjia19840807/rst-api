package com.cmacgm.gbs.rst.api.delegation.security;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.delegation.application.DelegationService;
import com.cmacgm.gbs.rst.api.delegation.domain.Delegation;
import com.cmacgm.gbs.rst.api.security.RstAuthenticationToken;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rebuilds the security principal when {@code X-Rst-Delegation-Id} is present.
 */
@Component
public class DelegationAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Rst-Delegation-Id";

    private final DelegationService delegations;
    private final TimesheetReadService timesheet;

    /**
     * @param delegations activation
     * @param timesheet subject display name
     */
    public DelegationAuthenticationFilter(DelegationService delegations, TimesheetReadService timesheet) {
        this.delegations = delegations;
        this.timesheet = timesheet;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String raw = request.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof RstAuthenticationToken token)
                || !(token.getPrincipal() instanceof RstPrincipal principal)
                || principal.isDelegated()) {
            filterChain.doFilter(request, response);
            return;
        }
        UUID delegationId;
        try {
            delegationId = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            writeDelegationEnded(request, response);
            return;
        }
        try {
            Delegation delegation = delegations.requireUsable(delegationId, principal.realCcgid());
            RstPrincipal effective = effectivePrincipal(principal, delegation);
            var authorities = effective.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                    .toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new RstAuthenticationToken(effective, token.getCredentials(), authorities));
        } catch (ApiException exception) {
            writeProblem(request, response, exception);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private RstPrincipal effectivePrincipal(RstPrincipal actor, Delegation delegation) {
        String subjectName = timesheet.displayNameByCcgid(delegation.getDelegatorCcgid());
        if (subjectName == null || subjectName.isBlank()) {
            subjectName = delegation.getDelegatorName();
        }
        return new RstPrincipal(
                delegation.getDelegatorCcgid(),
                subjectName,
                actor.email(),
                delegation.roleSet(),
                actor.scopes() == null ? Set.of() : actor.scopes(),
                delegation.getDelegatorCenter(),
                actor.ccgid(),
                actor.displayName(),
                delegation.getId());
    }

    private static void writeDelegationEnded(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        writeProblem(
                request,
                response,
                new ApiException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "delegation-inactive",
                        "Delegation ended. You are back to your own account."));
    }

    private static void writeProblem(
            HttpServletRequest request, HttpServletResponse response, ApiException exception)
            throws IOException {
        int status = exception.status().value();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"https://rst.cmacgm.com/problems/%s","title":"%s","status":%d,"detail":"%s","instance":"%s"}
                """.formatted(
                exception.code(),
                exception.status().getReasonPhrase(),
                status,
                escape(exception.getMessage()),
                escape(request.getRequestURI())).trim());
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
