package com.cmacgm.gbs.rst.api.domainhead.api.dto;

/**
 * One configured Domain Head on a Center × Domain row.
 *
 * @param positionId configured bindable position
 * @param ccgid current holder, if resolvable
 * @param name current holder display name
 * @param status CONFIGURED when the person is still in the Center, otherwise STALE
 */
public record DomainHeadAssigneeView(
        String positionId,
        String ccgid,
        String name,
        String status) {
}
