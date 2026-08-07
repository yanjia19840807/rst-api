package com.cmacgm.gbs.rst.api.tms.api.dto;

public record TmsSummaryResponse(
        long sessionsToday,
        long totalVolume,
        long pausedSessions) {
}
