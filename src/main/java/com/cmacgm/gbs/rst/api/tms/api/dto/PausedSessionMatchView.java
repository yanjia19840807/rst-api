package com.cmacgm.gbs.rst.api.tms.api.dto;

/**
 * Latest paused TMS session that matches the current agent, Toolkit, and reference.
 */
public record PausedSessionMatchView(TmsSessionResponse latest, long matchCount) {
}
