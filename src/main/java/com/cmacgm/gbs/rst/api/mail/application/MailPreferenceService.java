package com.cmacgm.gbs.rst.api.mail.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.mail.domain.MailPreference;
import com.cmacgm.gbs.rst.api.mail.domain.MailType;
import com.cmacgm.gbs.rst.api.mail.persistence.MailPreferenceRepository;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes mail switches for the signed-in role.
 */
@Service
public class MailPreferenceService {

    private final MailPreferenceRepository preferences;
    private final MailAddressLookup addresses;

    /**
     * @param preferences switches
     * @param addresses Timesheet emp_email
     */
    public MailPreferenceService(MailPreferenceRepository preferences, MailAddressLookup addresses) {
        this.preferences = preferences;
        this.addresses = addresses;
    }

    /**
     * @param principal real user
     * @return current switches
     */
    @Transactional(readOnly = true)
    public PreferenceView current(RstPrincipal principal) {
        requireOwnAccount(principal);
        String role = requireMailRole(principal);
        String ccgid = principal.ccgid();
        Map<String, Boolean> saved = saved(ccgid);
        List<TypeView> types = new ArrayList<>();
        for (MailType type : MailType.forRole(role)) {
            types.add(new TypeView(type.id(), type.label(), saved.getOrDefault(type.id(), true)));
        }
        String email = addresses.emailOf(ccgid);
        if (email == null) {
            email = "";
        }
        return new PreferenceView(role, email, email.isEmpty(), types);
    }

    /**
     * @param principal real user
     * @param request switches
     * @return saved view
     */
    @Transactional
    public PreferenceView save(RstPrincipal principal, PreferenceUpdate request) {
        requireOwnAccount(principal);
        String role = requireMailRole(principal);
        String ccgid = principal.ccgid().trim().toUpperCase(Locale.ROOT);
        List<MailType> allowed = MailType.forRole(role);
        Map<String, Boolean> incoming = new LinkedHashMap<>();
        for (TypeUpdate item : request == null || request.types() == null ? List.<TypeUpdate>of() : request.types()) {
            MailType type = MailType.fromId(item == null ? null : item.id());
            if (type == null || !allowed.contains(type)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_MAIL_TYPE",
                        "Unknown mail type for this role.");
            }
            incoming.put(type.id(), item.enabled());
        }
        for (MailType type : allowed) {
            boolean enabled = incoming.getOrDefault(type.id(), true);
            MailPreference row = preferences.findById(new MailPreference.Pk(ccgid, type.id()))
                    .orElseGet(() -> MailPreference.of(ccgid, type.id(), enabled));
            row.setEnabled(enabled);
            preferences.save(row);
        }
        return current(principal);
    }

    /**
     * Missing row means enabled.
     *
     * @param ccgid recipient
     * @param type mail type
     * @return whether to send
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(String ccgid, MailType type) {
        if (ccgid == null || type == null) {
            return false;
        }
        return preferences.findById(new MailPreference.Pk(ccgid.trim().toUpperCase(Locale.ROOT), type.id()))
                .map(MailPreference::isEnabled)
                .orElse(true);
    }

    private Map<String, Boolean> saved(String ccgid) {
        Map<String, Boolean> saved = new LinkedHashMap<>();
        for (MailPreference row : preferences.findByIdCcgidIgnoreCase(ccgid)) {
            saved.put(row.getMailType(), row.isEnabled());
        }
        return saved;
    }

    private static void requireOwnAccount(RstPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication is required.");
        }
        if (principal.isDelegated()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "mail-preference-own-account",
                    "Stop acting as someone else to change email preferences.");
        }
    }

    private static String requireMailRole(RstPrincipal principal) {
        String role = MailType.mailRole(principal.roles());
        if (role == null) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "mail-preference-forbidden",
                    "This role does not have email preferences.");
        }
        return role;
    }

    public record PreferenceView(String role, String email, boolean emailMissing, List<TypeView> types) {
    }

    public record TypeView(String id, String label, boolean enabled) {
    }

    public record PreferenceUpdate(List<TypeUpdate> types) {
    }

    public record TypeUpdate(String id, boolean enabled) {
    }
}
