package com.cmacgm.gbs.rst.api.security.sso;

import java.util.Locale;
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
     * @param raw token roles[0]
     * @param expectedEnv current deployment
     * @return parsed name and env
     */
    public static Parsed parse(String raw, String expectedEnv) {
        if (raw == null || raw.isBlank()) {
            throw new SsoException("sso-role-missing", "SSO role is missing.");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!value.startsWith(PREFIX)) {
            throw new SsoException("sso-role-invalid", "SSO role is not a RST application role.");
        }
        String rest = value.substring(PREFIX.length());
        int split = rest.lastIndexOf('_');
        if (split <= 0 || split == rest.length() - 1) {
            throw new SsoException("sso-role-invalid", "SSO role is not a RST application role.");
        }
        String name = rest.substring(0, split);
        String env = rest.substring(split + 1);
        if (!NAMES.contains(name) || !ENVS.contains(env)) {
            throw new SsoException("sso-role-invalid", "SSO role is not a RST application role.");
        }
        String expected = expectedEnv == null ? "" : expectedEnv.trim().toUpperCase(Locale.ROOT);
        if (!ENVS.contains(expected)) {
            throw new SsoException("sso-env-unconfigured", "SSO environment is not configured.");
        }
        if (!expected.equals(env)) {
            throw new SsoException("sso-env-mismatch", "SSO role does not match this environment.");
        }
        return new Parsed(name, env);
    }
}
