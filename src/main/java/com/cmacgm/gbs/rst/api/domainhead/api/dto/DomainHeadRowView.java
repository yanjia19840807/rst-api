package com.cmacgm.gbs.rst.api.domainhead.api.dto;

import java.util.List;

/**
 * One Domain mapping on the Center Roles page.
 *
 * @param domain GBS Domain
 * @param assignees configured Domain Heads, in selection order
 * @param status CONFIGURED when at least one person is still in the Center, MISSING when empty, otherwise STALE
 */
public record DomainHeadRowView(
        String domain,
        List<DomainHeadAssigneeView> assignees,
        String status) {
}
