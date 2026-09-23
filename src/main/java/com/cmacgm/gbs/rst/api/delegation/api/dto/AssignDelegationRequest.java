package com.cmacgm.gbs.rst.api.delegation.api.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * A parent position assigns one or more people to cover a direct child position.
 *
 * @param positionId child position
 * @param delegateCcgids people in the same Center
 * @param validFrom start, or null to begin immediately
 * @param validUntil end, or null when coverage does not expire
 */
public record AssignDelegationRequest(
        @NotBlank String positionId,
        @NotEmpty List<@NotBlank String> delegateCcgids,
        Instant validFrom,
        Instant validUntil) {
}
