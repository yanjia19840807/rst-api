package com.cmacgm.gbs.rst.api.identity.api.dto;

import java.util.List;

/**
 * Authenticated caller identity for the UI header / client session.
 */
public record CurrentUserResponse(
        String ccgid,
        String displayName,
        String email,
        List<String> roles,
        List<String> scopes) {
}
