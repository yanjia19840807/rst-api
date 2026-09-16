package com.cmacgm.gbs.rst.api.domainhead.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Dirty Center Roles mappings to persist.
 *
 * @param center GBS center; required for ADMIN, ignored for LTH (identity center)
 * @param lthPositionId Center LTH position; blank clears; omit to leave unchanged
 * @param mappings changed Domain Head rows; {@code positionId} blank clears the Domain
 */
public record SaveDomainHeadsRequest(
        String center, String lthPositionId, @Valid List<Mapping> mappings) {

    /**
     * One Domain mapping.
     *
     * @param domain GBS Domain
     * @param positionId bindable position, or blank to clear
     */
    public record Mapping(@NotBlank String domain, String positionId) {
    }
}
