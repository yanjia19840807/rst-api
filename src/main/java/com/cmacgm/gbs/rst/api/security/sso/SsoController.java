package com.cmacgm.gbs.rst.api.security.sso;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Azure AD authorization-code login. Redirect URLs match the App Registration.
 */
@RestController
@RequestMapping("/api/sso")
@Profile({"uat", "pre", "prod"})
public class SsoController {

    private final AzureOidcClient azure;
    private final SsoUserResolver users;
    private final SsoProperties properties;

    /**
     * @param azure token client
     * @param users claim → principal
     */
    public SsoController(AzureOidcClient azure, SsoUserResolver users, SsoProperties properties) {
        this.azure = azure;
        this.users = users;
        this.properties = properties;
    }

    /**
     * Starts Azure login.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param returnPath optional SPA path
     */
    @GetMapping("/auth")
    public void auth(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(name = "return", required = false) String returnPath) throws Exception {
        String state = SsoSessions.beginLogin(request, returnPath);
        String nonce = (String) request.getSession(true).getAttribute(SsoSessions.NONCE);
        response.sendRedirect(azure.authorizationUrl(state, nonce));
    }

    /**
     * Azure redirect. Exchanges the code and opens the SPA.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param code authorization code
     * @param state CSRF token
     * @param error Azure error
     * @param errorDescription Azure error text
     */
    @GetMapping("/callback")
    public void callback(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription)
            throws Exception {
        try {
            if (error != null && !error.isBlank()) {
                throw new SsoException("sso-azure-denied", errorDescription == null ? error : errorDescription);
            }
            if (code == null || code.isBlank()) {
                throw new SsoException("sso-code-missing", "Authorization code is missing.");
            }
            String nonce = SsoSessions.requireNonce(request, state);
            Jwt jwt = azure.exchangeCode(code, nonce);
            SsoSessions.establish(request, users.resolve(jwt));
            response.sendRedirect(SsoSessions.takeReturnPath(request));
        } catch (SsoException ex) {
            SsoSessions.clear(request);
            response.sendRedirect("/?ssoError=" + enc(ex.code()));
        }
    }

    /**
     * Clears the RST session and signs out of Azure.
     *
     * @param request HTTP request
     * @param response HTTP response
     */
    @GetMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) throws Exception {
        SsoSessions.clear(request);
        response.sendRedirect(azure.logoutUrl(properties.publicOrigin() + "/"));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
