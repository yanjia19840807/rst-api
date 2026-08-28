package com.cmacgm.gbs.rst.api.mail.application;

import java.time.Clock;
import java.util.Locale;

import com.cmacgm.gbs.rst.api.mail.domain.MailType;
import com.cmacgm.gbs.rst.api.mail.domain.SsoProfile;
import com.cmacgm.gbs.rst.api.mail.persistence.SsoProfileRepository;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records who last occupied a mail-capable role in a Center. Does not store email.
 */
@Service
public class SsoProfileService {

    private final SsoProfileRepository profiles;
    private final Clock clock;

    /**
     * @param profiles directory
     * @param clock timestamps
     */
    public SsoProfileService(SsoProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    /**
     * Records the real signed-in user. Skips delegates and roles without mail.
     *
     * @param principal current security principal
     */
    @Transactional
    public void touch(RstPrincipal principal) {
        if (principal == null || principal.isDelegated()) {
            return;
        }
        String role = MailType.mailRole(principal.roles());
        if (role == null) {
            return;
        }
        String ccgid = principal.ccgid() == null ? "" : principal.ccgid().trim().toUpperCase(Locale.ROOT);
        if (ccgid.isEmpty()) {
            return;
        }
        String center = blankToNull(principal.center());
        SsoProfile row = profiles.findById(ccgid).orElseGet(() -> SsoProfile.of(ccgid, center, role, clock.instant()));
        row.replace(ccgid, center, role, clock.instant());
        profiles.save(row);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
