package com.cmacgm.gbs.rst.api.security.sso;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Parses {@code CMACGM_APP_RST_{NAME}_{ENV}} without renaming NAME.
 */
public final class SsoRoleParser {

    public static final String PREFIX = "CMACGM_APP_RST_";
    public static final Set<String> NAMES = Set.of(
            "USER", "LOCAL_TRANSFORMATION_HEAD", "GOVERNANCE", "ADMIN");
    public static final Set<String> ENVS = Set.of("UAT", "PRE", "PROD");

    private SsoRoleParser() {
    }

    /**
     * Parsed SSO role.
     *
     * @param name USER / LOCAL_TRANSFORMATION_HEAD / GOVERNANCE / ADMIN
     * @param env UAT / PRE / PROD
     */
    public record Parsed(String name, String env) {
    }

    /**
     * Strict single-role parse (env must match).
     *
     * @param raw token role value
     * @param expectedEnv current deployment
     * @return parsed name and env
     */
    public static Parsed parse(String raw, String expectedEnv) {
        String expected = requireExpectedEnv(expectedEnv);
        Parsed parsed = tryParse(raw).orElseThrow(() -> new SsoException(
                raw == null || raw.isBlank() ? "sso-role-missing" : "sso-role-invalid",
                raw == null || raw.isBlank()
                        ? "SSO role is missing."
                        : "SSO role is not a RST application role."));
        if (!expected.equals(parsed.env())) {
            throw new SsoException("sso-env-mismatch", "SSO role does not match this environment.");
        }
        return parsed;
    }

    /**
     * Collects all RST App Role names that match the current deployment env.
     * Non-RST claims and wrong-env RST roles are skipped. Fails when none remain.
     *
     * @param rawRoles token {@code roles} claim values
     * @param expectedEnv current deployment
     * @return SSO names such as {@code USER}, {@code ADMIN}
     */
    public static Set<String> parseNames(Collection<String> rawRoles, String expectedEnv) {
        String expected = requireExpectedEnv(expectedEnv);
        if (rawRoles == null || rawRoles.isEmpty()) {
            throw new SsoException("sso-role-missing", "SSO role is missing.");
        }
        Set<String> matched = new LinkedHashSet<>();
        boolean sawWrongEnv = false;
        for (String raw : rawRoles) {
            Optional<Parsed> parsed = tryParse(raw);
            if (parsed.isEmpty()) {
                continue;
            }
            if (expected.equals(parsed.get().env())) {
                matched.add(parsed.get().name());
            } else {
                sawWrongEnv = true;
            }
        }
        if (!matched.isEmpty()) {
            return Set.copyOf(matched);
        }
        if (sawWrongEnv) {
            throw new SsoException("sso-env-mismatch", "SSO role does not match this environment.");
        }
        throw new SsoException("sso-role-missing", "SSO role is missing.");
    }

    /**
     * Best-effort parse without env check.
     *
     * @param raw token role value
     * @return parsed role when the value is a known RST App Role
     */
    public static Optional<Parsed> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!value.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = value.substring(PREFIX.length());
        int split = rest.lastIndexOf('_');
        if (split <= 0 || split == rest.length() - 1) {
            return Optional.empty();
        }
        String name = rest.substring(0, split);
        String env = rest.substring(split + 1);
        if (!NAMES.contains(name) || !ENVS.contains(env)) {
            return Optional.empty();
        }
        return Optional.of(new Parsed(name, env));
    }

    private static String requireExpectedEnv(String expectedEnv) {
        String expected = expectedEnv == null ? "" : expectedEnv.trim().toUpperCase(Locale.ROOT);
        if (!ENVS.contains(expected)) {
            throw new SsoException("sso-env-unconfigured", "SSO environment is not configured.");
        }
        return expected;
    }
}
