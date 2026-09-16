package com.cmacgm.gbs.rst.api.domainhead.api.dto;

import java.util.List;

/**
 * Center Roles page: one LTH plus Domain Head rows.
 *
 * @param center GBS center for the rows
 * @param dailyAvailable whether an ACTIVE Daily snapshot exists
 * @param monthlyAvailable whether an ACTIVE Monthly snapshot exists
 * @param remountedCount READY CDH / LTH steps remounted by the last save; null on GET
 * @param lth Center-level LTH assignee
 * @param domains one row per Monthly Domain in the Center
 */
public record DomainHeadPageView(
        String center,
        boolean dailyAvailable,
        boolean monthlyAvailable,
        Integer remountedCount,
        CenterRoleAssigneeView lth,
        List<DomainHeadRowView> domains) {
}
