package com.cmacgm.gbs.rst.api.security.sso;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Restores the SSO principal from the HTTP session.
 */
@Component
@Profile({"uat", "pre", "prod"})
public class SsoSessionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            RstPrincipal principal = SsoSessions.current(request);
            if (principal != null) {
                SsoSessions.apply(principal);
            }
        }
        filterChain.doFilter(request, response);
    }
}
