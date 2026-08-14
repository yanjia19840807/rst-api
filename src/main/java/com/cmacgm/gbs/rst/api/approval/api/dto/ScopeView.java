package com.cmacgm.gbs.rst.api.approval.api.dto;

/**
 * Submission scope view.
 */
public record ScopeView(
        String scopeLevel, String center, String site, String domain, String pl3Code,
        String carrier, String customerCountry) {
}
