package com.cmacgm.gbs.rst.api.delegation.api.dto;

/**
 * Timesheet person that A may pick as a delegate.
 */
public record DelegationCandidateView(String ccgid, String name, String center, String email) {
}
