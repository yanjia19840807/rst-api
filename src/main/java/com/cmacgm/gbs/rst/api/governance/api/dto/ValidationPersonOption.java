package com.cmacgm.gbs.rst.api.governance.api.dto;

/**
 * A person option for Validation Workflow Current Owner filter.
 *
 * @param ccgid Timesheet CCGID
 * @param name display name
 */
public record ValidationPersonOption(String ccgid, String name) {
}
