package com.cmacgm.gbs.rst.api.security;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * Authenticated caller. {@code ccgid} is the effective business identity;
 * when acting for someone else, {@code actorCcgid} is the real signed-in user.
 */
public record RstPrincipal(
        String ccgid,
        String displayName,
        String email,
        Set<String> roles,
        Set<String> scopes,
        String center,
        String actorCcgid,
        String actorDisplayName,
        UUID delegationId) implements Principal {

    private static final Set<String> GRANTABLE_ROLES = Set.of(
            "AGENT", "SUPERVISOR", "MANAGER", "CDH", "LTH");

    /**
     * Builds a non-delegated principal (actor equals effective identity).
     *
     * @param ccgid identity
     * @param displayName display name
     * @param email email
     * @param roles product roles
     * @param scopes token scopes
     * @param center GBS center
     */
    public RstPrincipal(
            String ccgid,
            String displayName,
            String email,
            Set<String> roles,
            Set<String> scopes,
            String center) {
        this(ccgid, displayName, email, roles, scopes, center, ccgid, displayName, null);
    }

    /**
     * Normalizes actor fields so a missing actor means "self".
     */
    public RstPrincipal {
        if (actorCcgid == null || actorCcgid.isBlank()) {
            actorCcgid = ccgid;
        }
        if (actorDisplayName == null || actorDisplayName.isBlank()) {
            actorDisplayName = displayName;
        }
    }

    @Override
    public String getName() {
        return ccgid;
    }

    /**
     * @return true when this request is acting through a delegation
     */
    public boolean isDelegated() {
        return delegationId != null;
    }

    /**
     * Real signed-in user, never the impersonated subject.
     *
     * @return actor CCGID
     */
    public String realCcgid() {
        return actorCcgid;
    }

    /**
     * Whether the real signed-in user may grant a delegation.
     *
     * @return true for AGENT / SUPERVISOR / MANAGER / CDH / LTH
     */
    public boolean canGrantDelegation() {
        if (isDelegated() || roles == null) {
            return false;
        }
        for (String role : roles) {
            if (GRANTABLE_ROLES.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
