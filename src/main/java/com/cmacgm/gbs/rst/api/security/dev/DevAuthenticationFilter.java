package com.cmacgm.gbs.rst.api.security.dev;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.cmacgm.gbs.rst.api.security.RstAuthenticationToken;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Injects a configurable demo principal in {@code dev}/{@code test}.
 *
 * <p>Set {@code app.security.dev-identity.ccgid} in {@code application-dev.yml} to a real
 * ACTIVE Timesheet {@code supervisor_ccgid} (or employee CCGID). Send
 * {@code X-Dev-Role: AGENT} to switch to {@code app.security.dev-identity.agent-ccgid}.
 */
@Component
@Profile({"dev", "test"})
@EnableConfigurationProperties(DevIdentityProperties.class)
public class DevAuthenticationFilter extends OncePerRequestFilter {

    private final DevIdentityProperties properties;
    private final DevIdentityService identities;

    /**
     * @param properties identity selection from configuration
     * @param identities resolver that ensures {@code app_user} exists
     */
    public DevAuthenticationFilter(DevIdentityProperties properties, DevIdentityService identities) {
        this.properties = properties;
        this.identities = identities;
    }

    /**
     * Places a demo authentication into the security context when none is present.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain remaining filter chain
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader("X-Dev-Role");
            boolean agentRole = header != null && "AGENT".equalsIgnoreCase(header.trim());
            String configuredRole = properties.getRole() == null
                    ? "SUPERVISOR"
                    : properties.getRole().trim().toUpperCase(Locale.ROOT);
            boolean useAgent = agentRole || "AGENT".equals(configuredRole);

            String ccgid = useAgent
                    ? firstNonBlank(properties.getAgentCcgid(), "AGENT001")
                    : firstNonBlank(properties.getCcgid(), "SUPERVISOR001");
            Set<String> roles = useAgent
                    ? Set.of("AGENT", "SUPERVISOR")
                    : Set.of("SUPERVISOR", "AGENT");

            RstPrincipal principal = identities.resolve(ccgid, roles);
            var authentication = new RstAuthenticationToken(
                    principal,
                    "dev-profile",
                    List.of(
                            new SimpleGrantedAuthority("ROLE_SUPERVISOR"),
                            new SimpleGrantedAuthority("ROLE_AGENT")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private static String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
