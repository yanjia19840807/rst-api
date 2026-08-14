package com.cmacgm.gbs.rst.api.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> roles = claimValues(jwt, "roles").stream()
                .map(role -> role.toUpperCase(Locale.ROOT).replace(' ', '_'))
                .collect(Collectors.toUnmodifiableSet());
        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        Set<String> scopes = scopeValues(jwt);
        String ccgid = firstNonBlank(jwt.getClaimAsString("CCGID"), jwt.getClaimAsString("ccgid"));
        RstPrincipal principal = new RstPrincipal(
                ccgid == null ? jwt.getSubject() : ccgid.trim().toUpperCase(Locale.ROOT),
                firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("preferred_username")),
                firstNonBlank(jwt.getClaimAsString("email"), jwt.getClaimAsString("preferred_username")),
                roles,
                scopes);
        return new RstAuthenticationToken(principal, jwt.getTokenValue(), authorities);
    }

    private static Collection<String> claimValues(Jwt jwt, String name) {
        Collection<String> values = jwt.getClaimAsStringList(name);
        return values == null ? Set.of() : values;
    }

    private static Set<String> scopeValues(Jwt jwt) {
        String value = jwt.getClaimAsString("scp");
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.trim().split("\\s+"))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
