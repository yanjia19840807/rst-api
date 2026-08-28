package com.cmacgm.gbs.rst.api.config;

import com.cmacgm.gbs.rst.api.delegation.security.DelegationAuthenticationFilter;
import com.cmacgm.gbs.rst.api.security.JwtPrincipalConverter;
import com.cmacgm.gbs.rst.api.security.ProblemSecurityHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@Profile("!dev & !test")
public class ProductionSecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.azure-tenant-id}") String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("AZURE_TENANT_ID is required outside dev and test profiles");
        }
        return JwtDecoders.fromIssuerLocation(
                "https://login.microsoftonline.com/" + tenantId + "/v2.0");
    }

    @Bean
    SecurityFilterChain productionSecurityFilterChain(
            HttpSecurity http,
            JwtPrincipalConverter principalConverter,
            DelegationAuthenticationFilter delegationAuthenticationFilter,
            ProblemSecurityHandler problemSecurityHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemSecurityHandler)
                        .accessDeniedHandler(problemSecurityHandler))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(principalConverter)))
                .addFilterAfter(delegationAuthenticationFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }
}
