package com.cmacgm.gbs.rst.api.security.api;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.mail.application.SsoProfileService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.api.dto.CurrentUserResponse;
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

    private final SsoProfileService profiles;

    /**
     * @param profiles SSO directory
     */
    public MeController(SsoProfileService profiles) {
        this.profiles = profiles;
    }

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
        profiles.touch(principal);
        return CurrentUserResponse.from(principal);
    }
}
