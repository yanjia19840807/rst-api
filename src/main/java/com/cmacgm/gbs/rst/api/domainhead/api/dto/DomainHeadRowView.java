package com.cmacgm.gbs.rst.api.domainhead.api.dto;

/**
 * One Domain mapping on the LTH page.
 *
 * @param domain GBS Domain
 * @param positionId configured bindable position, if any
 * @param ccgid current holder, if resolvable
 * @param name current holder display name
 * @param status CONFIGURED / MISSING / STALE
 */
public record DomainHeadRowView(
        String domain,
        String positionId,
        String ccgid,
        String name,
        String status) {
}
