package com.cmacgm.gbs.rst.api.security.api.dto;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.security.RstPrincipal;

/**
 * Authenticated caller identity for the UI header / client session.
 */
public record CurrentUserResponse(
        String ccgid,
        String displayName,
        String email,
        List<String> roles,
        List<String> scopes,
        String center,
        String jobRole,
        ActorView actor,
        UUID delegationId,
        String delegatedPositionId,
        List<String> delegatedPositionRoles,
        String delegatedOccupantName,
        Boolean devOverrideEnabled) {

    /**
     * Maps the current principal.
     *
     * @param principal security principal
     * @return response
     */
    public static CurrentUserResponse from(RstPrincipal principal) {
        return from(principal, null, null);
    }

    /**
     * Maps the current principal and whether header/query identity override is on.
     *
     * @param principal security principal
     * @param devOverrideEnabled {@code true} when {@code app.security.dev-identity.override-enabled}
     *        is on; {@code null} outside {@code dev}/{@code test}
     * @return response
     */
    public static CurrentUserResponse from(RstPrincipal principal, Boolean devOverrideEnabled) {
        return from(principal, devOverrideEnabled, null);
    }

    /**
     * Maps the current principal, override flag, and Timesheet job role.
     *
     * @param principal security principal
     * @param devOverrideEnabled {@code true} when {@code app.security.dev-identity.override-enabled}
     *        is on; {@code null} outside {@code dev}/{@code test}
     * @param jobRole Timesheet {@code emp_job_role} when the caller is in the ACTIVE Daily snapshot
     * @return response
     */
    public static CurrentUserResponse from(
            RstPrincipal principal, Boolean devOverrideEnabled, String jobRole) {
        return from(principal, devOverrideEnabled, jobRole, List.of(), null);
    }

    /**
     * @param delegatedPositionRoles roles covered by {@code delegatedPositionId}; empty otherwise
     * @param delegatedOccupantName current occupant of {@code delegatedPositionId}, if any
     */
    public static CurrentUserResponse from(
            RstPrincipal principal,
            Boolean devOverrideEnabled,
            String jobRole,
            List<String> delegatedPositionRoles,
            String delegatedOccupantName) {
        ActorView actor = new ActorView(principal.actorCcgid(), principal.actorDisplayName());
        List<String> coveredRoles = principal.delegatedPositionId() == null
                ? List.of()
                : List.copyOf(delegatedPositionRoles);
        return new CurrentUserResponse(
                principal.ccgid(),
                principal.displayName(),
                principal.email(),
                List.copyOf(principal.roles()),
                List.copyOf(principal.scopes()),
                principal.center(),
                jobRole,
                actor,
                principal.delegationId(),
                principal.delegatedPositionId(),
                coveredRoles,
                principal.delegatedPositionId() == null ? null : delegatedOccupantName,
                devOverrideEnabled);
    }

    /**
     * Real signed-in user.
     */
    public record ActorView(String ccgid, String displayName) {
    }
}
