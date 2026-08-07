package com.cmacgm.gbs.rst.api.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        writeProblem(
                response,
                request,
                HttpServletResponse.SC_UNAUTHORIZED,
                "authentication-required",
                "Authentication required",
                "A valid bearer token is required.");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        writeProblem(
                response,
                request,
                HttpServletResponse.SC_FORBIDDEN,
                "access-denied",
                "Access denied",
                "The current user is not allowed to perform this operation.");
    }

    private static void writeProblem(
            HttpServletResponse response,
            HttpServletRequest request,
            int status,
            String code,
            String title,
            String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"https://rst.cmacgm.com/problems/%s","title":"%s","status":%d,"detail":"%s","instance":"%s"}
                """.formatted(
                code,
                title,
                status,
                detail,
                escape(request.getRequestURI())).trim());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
