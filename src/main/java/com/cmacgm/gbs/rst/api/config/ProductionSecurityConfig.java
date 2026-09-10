package com.cmacgm.gbs.rst.api.config;

import com.cmacgm.gbs.rst.api.delegation.security.DelegationAuthenticationFilter;
import com.cmacgm.gbs.rst.api.security.ProblemSecurityHandler;
import com.cmacgm.gbs.rst.api.security.sso.SsoSessionFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@Profile({"uat", "pre", "prod"})
public class ProductionSecurityConfig {

    @Bean
    SecurityFilterChain productionSecurityFilterChain(
            HttpSecurity http,
            SsoSessionFilter ssoSessionFilter,
            DelegationAuthenticationFilter delegationAuthenticationFilter,
            ProblemSecurityHandler problemSecurityHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/api/sso/auth",
                                "/api/sso/callback",
                                "/api/sso/logout")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemSecurityHandler)
                        .accessDeniedHandler(problemSecurityHandler))
                .addFilterBefore(ssoSessionFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(delegationAuthenticationFilter, SsoSessionFilter.class)
                .build();
    }
}
