package com.cmacgm.gbs.rst.api.config;

import com.cmacgm.gbs.rst.api.security.ProblemSecurityHandler;
import com.cmacgm.gbs.rst.api.security.dev.DevAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Security chain for the {@code test} profile: authenticated requests with method security enabled
 * so role-guard integration tests remain meaningful.
 */
@Configuration
@EnableMethodSecurity
@Profile("test")
public class TestSecurityConfig {

    /**
     * Builds the test Security filter chain.
     *
     * @param http HTTP security builder
     * @param devAuthenticationFilter demo principal filter
     * @param problemSecurityHandler problem details for auth failures
     * @return security filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    SecurityFilterChain testSecurityFilterChain(
            HttpSecurity http,
            DevAuthenticationFilter devAuthenticationFilter,
            ProblemSecurityHandler problemSecurityHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemSecurityHandler)
                        .accessDeniedHandler(problemSecurityHandler))
                .addFilterBefore(devAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .build();
    }
}
