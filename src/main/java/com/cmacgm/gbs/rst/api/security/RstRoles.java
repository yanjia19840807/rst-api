package com.cmacgm.gbs.rst.api.security;

import java.util.Set;

/**
 * Canonical RST product roles. Same codes as Timesheet {@code roleType} and SSO NAME
 * (except SSO {@code USER}, which resolves to a Timesheet role).
 */
public final class RstRoles {

    public static final String AGENT = "AGENT";
    public static final String SUPERVISOR = "SUPERVISOR";
    public static final String SR_MANAGER = "SR_MANAGER";
    public static final String DOMAIN_HEAD = "DOMAIN_HEAD";
    public static final String LOCAL_TRANSFORMATION_HEAD = "LOCAL_TRANSFORMATION_HEAD";
    public static final String GOVERNANCE = "GOVERNANCE";
    public static final String ADMIN = "ADMIN";

    public static final Set<String> ALL = Set.of(
            AGENT,
            SUPERVISOR,
            SR_MANAGER,
            DOMAIN_HEAD,
            LOCAL_TRANSFORMATION_HEAD,
            GOVERNANCE,
            ADMIN);

    public static final Set<String> TIMESHEET_USER_ROLES = Set.of(
            AGENT, SUPERVISOR, SR_MANAGER, DOMAIN_HEAD);

    public static final Set<String> GRANTABLE = Set.of(
            AGENT, SUPERVISOR, SR_MANAGER, DOMAIN_HEAD, LOCAL_TRANSFORMATION_HEAD);

    private RstRoles() {
    }
}
