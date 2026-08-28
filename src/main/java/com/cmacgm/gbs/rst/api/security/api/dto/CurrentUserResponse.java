package com.cmacgm.gbs.rst.api.security.api.dto;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.security.RstPrincipal;

/**
 * Authenticated caller identity for the UI header / client session.
 */
public record CurrentUserResponse(
        String ccgid,
        String displayName,
        String email,
        List<String> roles,
        List<String> scopes,
        String center,
        ActorView actor,
        UUID delegationId) {

    /**
     * Maps the current principal.
     *
     * @param principal security principal
     * @return response
     */
    public static CurrentUserResponse from(RstPrincipal principal) {
        ActorView actor = new ActorView(principal.actorCcgid(), principal.actorDisplayName());
        return new CurrentUserResponse(
                principal.ccgid(),
                principal.displayName(),
                principal.email(),
                List.copyOf(principal.roles()),
                List.copyOf(principal.scopes()),
                principal.center(),
                actor,
                principal.delegationId());
    }

    /**
     * Real signed-in user.
     */
    public record ActorView(String ccgid, String displayName) {
    }
}
