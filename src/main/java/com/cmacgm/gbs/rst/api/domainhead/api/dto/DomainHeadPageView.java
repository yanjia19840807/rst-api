package com.cmacgm.gbs.rst.api.domainhead.api.dto;

import java.util.List;

/**
 * LTH Domain Head configuration page.
 *
 * @param center LTH Center from SSO / dev identity
 * @param dailyAvailable whether an ACTIVE Daily snapshot exists
 * @param remountedCount READY CDH steps remounted by the last save; null on GET
 * @param domains one row per Daily Domain in the Center
 */
public record DomainHeadPageView(
        String center,
        boolean dailyAvailable,
        Integer remountedCount,
        List<DomainHeadRowView> domains) {
}
