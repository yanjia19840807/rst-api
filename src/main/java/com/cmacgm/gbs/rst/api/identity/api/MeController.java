package com.cmacgm.gbs.rst.api.identity.api;

import java.util.ArrayList;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.identity.api.dto.CurrentUserResponse;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the authenticated principal used by the web client header/session.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    /**
     * @param principal current security principal
     * @return caller identity (CCGID, display name, roles)
     */
    @GetMapping
    public CurrentUserResponse me(@AuthenticationPrincipal RstPrincipal principal) {
        if (principal == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthenticated",
                    "Authentication is required.");
        }
        return new CurrentUserResponse(
                principal.ccgid(),
                principal.displayName(),
                principal.email(),
                new ArrayList<>(principal.roles()),
                new ArrayList<>(principal.scopes()),
                principal.center());
    }
}
