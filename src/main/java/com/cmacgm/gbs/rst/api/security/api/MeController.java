package com.cmacgm.gbs.rst.api.security.api;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.mail.application.SsoProfileService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.api.dto.CurrentUserResponse;
import com.cmacgm.gbs.rst.api.security.dev.DevIdentityProperties;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<DevIdentityProperties> devIdentity;

    /**
     * @param profiles SSO directory
     * @param devIdentity optional dev-identity settings ({@code dev}/{@code test} only)
     */
    public MeController(
            SsoProfileService profiles, ObjectProvider<DevIdentityProperties> devIdentity) {
        this.profiles = profiles;
        this.devIdentity = devIdentity;
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
        DevIdentityProperties properties = devIdentity.getIfAvailable();
        Boolean overrideEnabled = properties == null ? null : properties.isOverrideEnabled();
        return CurrentUserResponse.from(principal, overrideEnabled);
    }
}
