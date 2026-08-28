package com.cmacgm.gbs.rst.api.delegation.api.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A grants B the right to act as A.
 */
public record CreateDelegationRequest(
        @NotBlank String delegateCcgid,
        @NotNull Instant validFrom,
        @NotNull Instant validUntil) {
}
