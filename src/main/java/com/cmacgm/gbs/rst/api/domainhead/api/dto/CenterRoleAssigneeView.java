package com.cmacgm.gbs.rst.api.domainhead.api.dto;

/**
 * One Center-level assignee (LTH) on the Center Roles page.
 *
 * @param positionId configured bindable position, if any
 * @param ccgid current holder, if resolvable
 * @param name current holder display name
 * @param status CONFIGURED / MISSING / STALE
 */
public record CenterRoleAssigneeView(
        String positionId,
        String ccgid,
        String name,
        String status) {
}
