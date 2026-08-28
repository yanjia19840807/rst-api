package com.cmacgm.gbs.rst.api.delegation.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.delegation.domain.Delegation;

/**
 * Delegation row for settings and Act-as lists.
 */
public record DelegationView(
        UUID id,
        String delegatorCcgid,
        String delegatorName,
        String delegateCcgid,
        String delegateName,
        List<String> delegatorRoles,
        String delegatorCenter,
        Instant validFrom,
        Instant validUntil,
        String status,
        Instant createdAt,
        Instant endedAt) {

    /**
     * Maps a persisted row.
     *
     * @param row delegation
     * @return view
     */
    public static DelegationView from(Delegation row) {
        return new DelegationView(
                row.getId(),
                row.getDelegatorCcgid(),
                row.getDelegatorName(),
                row.getDelegateCcgid(),
                row.getDelegateName(),
                List.copyOf(row.roleSet()),
                row.getDelegatorCenter(),
                row.getValidFrom(),
                row.getValidUntil(),
                row.getStatus().name(),
                row.getCreatedAt(),
                row.endedAt());
    }
}
