package com.cmacgm.gbs.rst.api.security.sso;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Azure AD confidential-client settings for RST SSO.
 *
 * @param tenantId Entra tenant
 * @param clientId app registration
 * @param clientSecret app secret
 * @param redirectUri registered {@code /api/sso/callback}
 * @param env UAT / PRE / PROD; must match the token role suffix
 */
@ConfigurationProperties("app.security.sso")
public record SsoProperties(
        String tenantId,
        String clientId,
        String clientSecret,
        String redirectUri,
        String env) {

    /**
     * @return issuer used to validate ID tokens
     */
    public String issuer() {
        return "https://login.microsoftonline.com/" + tenantId().trim() + "/v2.0";
    }

    /**
     * @return authorize endpoint
     */
    public String authorizeUri() {
        return "https://login.microsoftonline.com/" + tenantId().trim() + "/oauth2/v2.0/authorize";
    }

    /**
     * @return token endpoint
     */
    public String tokenUri() {
        return "https://login.microsoftonline.com/" + tenantId().trim() + "/oauth2/v2.0/token";
    }

    /**
     * @return Azure logout endpoint
     */
    public String logoutUri() {
        return "https://login.microsoftonline.com/" + tenantId().trim() + "/oauth2/v2.0/logout";
    }

    /**
     * Public site origin derived from the registered redirect URI.
     *
     * @return origin with no trailing slash
     */
    public String publicOrigin() {
        String redirect = redirectUri() == null ? "" : redirectUri().trim();
        int marker = redirect.indexOf("/api/");
        return marker > 0 ? redirect.substring(0, marker) : redirect;
    }
}
