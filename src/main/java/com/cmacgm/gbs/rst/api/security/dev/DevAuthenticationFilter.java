package com.cmacgm.gbs.rst.api.security.dev;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.cmacgm.gbs.rst.api.security.RstAuthenticationToken;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cmacgm.gbs.rst.api.common.error.ApiException;

/**
 * Injects a configurable demo principal in {@code dev}/{@code test}.
 *
 * <p>When {@code override-enabled} is {@code true}, identity comes from the SPA via
 * {@code X-Dev-Ccgid}, {@code X-Dev-Role}, and {@code X-Dev-Center}. A CCGID without
 * a role uses every ACTIVE Daily seat. Requests without those headers default to
 * {@code ADMIN001} / {@code ADMIN}.
 */
@Component
@Profile({"dev", "test"})
@EnableConfigurationProperties(DevIdentityProperties.class)
public class DevAuthenticationFilter extends OncePerRequestFilter {

    private final DevIdentityProperties properties;
    private final DevIdentityService identities;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            String headerCcgid = override ? request.getHeader("X-Dev-Ccgid") : null;
            String headerRole = override ? request.getHeader("X-Dev-Role") : null;
            String ccgid = firstNonBlank(headerCcgid, properties.getCcgid(), "ADMIN001");
            Set<String> roles;
            try {
                if (headerRole != null && !headerRole.isBlank()) {
                    roles = Set.of(DevRoles.requireValid(headerRole));
                } else if (headerCcgid != null && !headerCcgid.isBlank()) {
                    roles = identities.timesheetRoles(ccgid);
                } else {
                    roles = Set.of(DevRoles.requireValid(firstNonBlank(properties.getRole(), null, "ADMIN")));
                }
            } catch (IllegalArgumentException ex) {
                writeProblem(response, request, new ApiException(
                        HttpStatus.BAD_REQUEST, "dev-identity-role", ex.getMessage()));
                return;
            } catch (ApiException ex) {
                writeProblem(response, request, ex);
                return;
            }

            String center = firstNonBlankPreserveCase(
                    override ? request.getHeader("X-Dev-Center") : null,
                    properties.getCenter());
            RstPrincipal principal = identities.resolve(ccgid, roles, center);
            var authentication = new RstAuthenticationToken(
                    principal,
                    "dev-profile",
                    roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private void writeProblem(
            HttpServletResponse response, HttpServletRequest request, ApiException exception)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                exception.status(), exception.getMessage());
        problem.setTitle(exception.status().getReasonPhrase());
        problem.setType(URI.create("https://rst.cmacgm.com/problems/" + exception.code()));
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(exception.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
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
