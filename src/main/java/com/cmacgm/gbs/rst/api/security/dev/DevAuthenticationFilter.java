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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cmacgm.gbs.rst.api.common.error.ApiException;

/**
 * Injects a configurable demo principal in {@code dev}/{@code test}.
 *
 * <p>When {@code override-enabled} is {@code true}, identity comes from the SPA via
 * {@code X-Dev-Ccgid}, {@code X-Dev-Role}, and {@code X-Dev-Center}. Requests
 * without those headers default to {@code ADMIN001} / {@code ADMIN}.
 */
@Component
@Profile({"dev", "test"})
@EnableConfigurationProperties(DevIdentityProperties.class)
public class DevAuthenticationFilter extends OncePerRequestFilter {

    private final DevIdentityProperties properties;
    private final DevIdentityService identities;

    /**
     * @param properties identity selection from configuration
     * @param identities resolver for the configured CCGID
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
            boolean override = properties.isOverrideEnabled();
            String ccgid = firstNonBlank(
                    override ? request.getHeader("X-Dev-Ccgid") : null,
                    properties.getCcgid(),
                    "ADMIN001");
            String role;
            try {
                role = DevRoles.requireValid(firstNonBlank(
                        override ? request.getHeader("X-Dev-Role") : null,
                        properties.getRole(),
                        "ADMIN"));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "dev-identity-role", ex.getMessage());
            }

            Set<String> roles = Set.of(role);
            String center = firstNonBlankPreserveCase(
                    override ? request.getHeader("X-Dev-Center") : null,
                    properties.getCenter());
            RstPrincipal principal = identities.resolve(ccgid, roles, center);
            var authentication = new RstAuthenticationToken(
                    principal,
                    "dev-profile",
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim().toUpperCase(Locale.ROOT);
        }
        if (second != null && !second.isBlank()) {
            return second.trim().toUpperCase(Locale.ROOT);
        }
        return fallback;
    }

    private static String firstNonBlankPreserveCase(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}
