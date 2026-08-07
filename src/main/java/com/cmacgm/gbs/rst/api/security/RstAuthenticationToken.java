package com.cmacgm.gbs.rst.api.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public final class RstAuthenticationToken extends AbstractAuthenticationToken {

    private final RstPrincipal principal;
    private final Object credentials;

    public RstAuthenticationToken(
            RstPrincipal principal,
            Object credentials,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public RstPrincipal getPrincipal() {
        return principal;
    }
}
