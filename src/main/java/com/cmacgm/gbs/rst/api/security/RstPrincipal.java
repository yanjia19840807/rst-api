package com.cmacgm.gbs.rst.api.security;

import java.security.Principal;
import java.util.Set;

public record RstPrincipal(
        String ccgid,
        String displayName,
        String email,
        Set<String> roles,
        Set<String> scopes,
        String center) implements Principal {

    @Override
    public String getName() {
        return ccgid;
    }
}
