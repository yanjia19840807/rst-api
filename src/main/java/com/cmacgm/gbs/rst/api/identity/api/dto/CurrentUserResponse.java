package com.cmacgm.gbs.rst.api.identity.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Authenticated caller identity for the UI header / client session.
 */
public record CurrentUserResponse(
        UUID userId,
        String ccgid,
        String displayName,
        String email,
        List<String> roles,
        List<String> scopes) {
}
