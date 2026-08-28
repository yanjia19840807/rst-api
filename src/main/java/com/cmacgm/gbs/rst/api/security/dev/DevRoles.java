package com.cmacgm.gbs.rst.api.security.dev;

import java.util.Locale;
import java.util.Set;

/**
 * Allowed RST roles for local {@code dev}/{@code test} identity simulation.
 */
public final class DevRoles {

    public static final Set<String> ALL = Set.of(
            "AGENT",
            "SUPERVISOR",
            "MANAGER",
            "CDH",
            "LTH",
            "HO",
            "ADMIN");

    private DevRoles() {
    }

    /**
     * Normalizes and validates a configured / header role value.
     *
     * @param raw role text
     * @return uppercase role code
     * @throws IllegalArgumentException when the role is missing or unsupported
     */
    public static String requireValid(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "app.security.dev-identity.role must be one of " + ALL);
        }
        String role = raw.trim().toUpperCase(Locale.ROOT);
        if (!ALL.contains(role)) {
            throw new IllegalArgumentException(
                    "Unsupported dev-identity role '" + role + "'. Allowed: " + ALL);
        }
        return role;
    }
}
