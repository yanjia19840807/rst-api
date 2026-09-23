package com.cmacgm.gbs.rst.api.delegation.api.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * The signed-in user delegates every position they occupy to one or more people.
 * A null start begins immediately. A null end does not expire.
 *
 * @param positionId ignored; every occupied position is covered
 * @param delegateCcgids people in the same Center
 * @param validFrom start, or null to begin immediately
 * @param validUntil end, or null when coverage does not expire
 */
public record CreateDelegationRequest(
        String positionId,
        @NotEmpty List<@NotBlank String> delegateCcgids,
        Instant validFrom,
        Instant validUntil) {
}
