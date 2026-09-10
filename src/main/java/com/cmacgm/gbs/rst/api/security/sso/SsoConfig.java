package com.cmacgm.gbs.rst.api.security.sso;

import java.util.Locale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;

/**
 * Azure ID-token decoder for the SSO callback. Not used as a resource server.
 */
@Configuration
@Profile({"uat", "pre", "prod"})
@EnableConfigurationProperties(SsoProperties.class)
public class SsoConfig {

    /**
     * @param properties tenant / client
     * @return decoder pinned to the Entra issuer
     */
    @Bean
    JwtDecoder jwtDecoder(SsoProperties properties) {
        if (properties.tenantId() == null || properties.tenantId().isBlank()) {
            throw new IllegalStateException("AZURE_TENANT_ID is required in uat, pre, and prod");
        }
        if (properties.clientId() == null || properties.clientId().isBlank()) {
            throw new IllegalStateException("AZURE_CLIENT_ID is required in uat, pre, and prod");
        }
        if (properties.redirectUri() == null || properties.redirectUri().isBlank()) {
            throw new IllegalStateException("AZURE_REDIRECT_URI is required in uat, pre, and prod");
        }
        if (properties.clientSecret() == null || properties.clientSecret().isBlank()) {
            throw new IllegalStateException("AZURE_CLIENT_SECRET is required in uat, pre, and prod");
        }
        String env = properties.env() == null ? "" : properties.env().trim().toUpperCase(Locale.ROOT);
        if (!SsoRoleParser.ENVS.contains(env)) {
            throw new IllegalStateException("SSO_ENV must be UAT, PRE, or PROD");
        }
        return JwtDecoders.fromIssuerLocation(properties.issuer());
    }
}
