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
 * Temporary open security for local {@code dev} walkthrough only.
 * {@code @PreAuthorize} is disabled; HTTP auth is {@code permitAll}.
 * {@link DevAuthenticationFilter} still injects a principal for {@code userId}/{@code ccgid}.
 * Restore role checks after the Supervisor → Submit path is stable.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = false)
@Profile("dev")
public class DevSecurityConfig {

    /**
     * Builds an open Security filter chain for local development.
     *
     * @param http HTTP security builder
     * @param devAuthenticationFilter filter that injects the demo principal
     * @param problemSecurityHandler kept for consistent error mapping
     * @return security filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    SecurityFilterChain devSecurityFilterChain(
            HttpSecurity http,
            DevAuthenticationFilter devAuthenticationFilter,
            ProblemSecurityHandler problemSecurityHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemSecurityHandler)
                        .accessDeniedHandler(problemSecurityHandler))
                .addFilterBefore(devAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .build();
    }
}
