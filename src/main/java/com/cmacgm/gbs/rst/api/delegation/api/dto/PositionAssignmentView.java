package com.cmacgm.gbs.rst.api.delegation.api.dto;

import java.util.List;

/**
 * One direct child position and the people currently covering it.
 *
 * @param positionId child position
 * @param roles every role on that position
 * @param occupantName current occupants, if any
 * @param occupantCcgid first occupant, if any
 * @param delegations open coverage rows
 */
public record PositionAssignmentView(
        String positionId,
        List<String> roles,
        String occupantName,
        String occupantCcgid,
        List<DelegationView> delegations) {
}
