package com.cmacgm.gbs.rst.api.security;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

public record RstPrincipal(
        UUID userId,
        String ccgid,
        String displayName,
        String email,
        Set<String> roles,
        Set<String> scopes) implements Principal {

    @Override
    public String getName() {
        return ccgid;
    }
}
