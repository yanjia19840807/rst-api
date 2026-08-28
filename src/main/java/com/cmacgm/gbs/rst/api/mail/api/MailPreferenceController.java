package com.cmacgm.gbs.rst.api.mail.api;

import com.cmacgm.gbs.rst.api.mail.application.MailPreferenceService;
import com.cmacgm.gbs.rst.api.mail.application.MailPreferenceService.PreferenceUpdate;
import com.cmacgm.gbs.rst.api.mail.application.MailPreferenceService.PreferenceView;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signed-in user's mail switches.
 */
@RestController
@RequestMapping("/api/v1/me/mail-preferences")
public class MailPreferenceController {

    private final MailPreferenceService preferences;

    /**
     * @param preferences switches
     */
    public MailPreferenceController(MailPreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public PreferenceView current(@AuthenticationPrincipal RstPrincipal principal) {
        return preferences.current(principal);
    }

    @PutMapping
    public PreferenceView save(
            @AuthenticationPrincipal RstPrincipal principal, @RequestBody PreferenceUpdate request) {
        return preferences.save(principal, request);
    }
}
