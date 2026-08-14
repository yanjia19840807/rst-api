package com.cmacgm.gbs.rst.api.tms.api.dto;

import java.math.BigDecimal;

public record TmsSummaryResponse(
        long sessionsToday,
        BigDecimal totalVolume,
        long pausedSessions) {
}
