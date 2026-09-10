package com.cmacgm.gbs.rst.api.security.sso;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Authorization-code exchange against Microsoft identity platform.
 */
@Component
@Profile({"uat", "pre", "prod"})
public class AzureOidcClient {

    private final SsoProperties properties;
    private final JwtDecoder jwtDecoder;
    private final RestClient restClient;

    /**
     * @param properties client settings
     * @param jwtDecoder ID-token signature / issuer check
     */
    public AzureOidcClient(SsoProperties properties, JwtDecoder jwtDecoder) {
        this.properties = properties;
        this.jwtDecoder = jwtDecoder;
        this.restClient = RestClient.create();
    }

    /**
     * @param state CSRF token
     * @param nonce ID-token nonce
     * @return Azure authorize URL
     */
    public String authorizationUrl(String state, String nonce) {
        return properties.authorizeUri()
                + "?client_id=" + enc(properties.clientId())
                + "&response_type=code"
                + "&redirect_uri=" + enc(properties.redirectUri())
                + "&response_mode=query"
                + "&scope=" + enc("openid profile email")
                + "&state=" + enc(state)
                + "&nonce=" + enc(nonce);
    }

    /**
     * Exchanges the code and validates the ID token.
     *
     * @param code authorization code
     * @param expectedNonce nonce stored at /auth
     * @return validated ID token
     */
    public Jwt exchangeCode(String code, String expectedNonce) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        form.add("scope", "openid profile email");
        Map<?, ?> body;
        try {
            body = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException ex) {
            throw new SsoException("sso-token-exchange", "Could not complete sign-in with Azure AD.");
        }
        if (body == null || body.get("id_token") == null) {
            throw new SsoException("sso-id-token-missing", "Azure AD did not return an ID token.");
        }
        Jwt jwt = jwtDecoder.decode(String.valueOf(body.get("id_token")));
        if (!properties.clientId().equals(jwt.getAudience().stream().findFirst().orElse(null))
                && jwt.getAudience().stream().noneMatch(properties.clientId()::equals)) {
            throw new SsoException("sso-audience-invalid", "The sign-in token is not for this application.");
        }
        String nonce = jwt.getClaimAsString("nonce");
        if (expectedNonce == null || !expectedNonce.equals(nonce)) {
            throw new SsoException("sso-nonce-invalid", "The sign-in token nonce does not match.");
        }
        return jwt;
    }

    /**
     * @param postLogoutRedirect frontend origin + /
     * @return Azure logout URL
     */
    public String logoutUrl(String postLogoutRedirect) {
        return properties.logoutUri() + "?post_logout_redirect_uri=" + enc(postLogoutRedirect);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
