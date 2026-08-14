package com.cmacgm.gbs.rst.api.tms.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;

public record TmsSessionResponse(
        String id,
        UUID toolkitId,
        UUID subtaskId,
        String toolkitName,
        String subtaskName,
        String agentName,
        String agentCcgid,
        String domain,
        String pl1,
        String pl2,
        String pl3Code,
        String pl3Name,
        BigDecimal processedVolume,
        String reference,
        String remarks,
        String status,
        Instant startedAt,
        Instant pausedAt,
        Instant endedAt,
        long netDurationSeconds,
        long grossDurationSeconds,
        long pauseDurationSeconds,
        String discardReason,
        long version) {

    public static TmsSessionResponse from(TmsSession session, Instant now) {
        Instant wireStartedAt = session.getRunningSince() == null
                ? session.getStartedAt()
                : session.getRunningSince();
        long wireNetDuration = session.elapsedSeconds(now);
        return new TmsSessionResponse(
                session.getSessionNo(),
                session.getToolkit().getId(),
                session.getToolkitSubtask() == null ? null : session.getToolkitSubtask().getId(),
                session.getToolkitNameSnapshot(),
                session.getSubtaskNameSnapshot(),
                session.getUser().getDisplayName(),
                session.getUser().getCcgid(),
                session.getDomainSnapshot(),
                session.getPl1Snapshot(),
                session.getPl2Snapshot(),
                session.getPl3CodeSnapshot(),
                session.getPl3NameSnapshot(),
                session.getProcessedVolume(),
                session.getReference(),
                session.getRemarks(),
                session.getStatus().name().toLowerCase(Locale.ROOT),
                wireStartedAt,
                session.getPausedAt(),
                session.getEndedAt(),
                wireNetDuration,
                session.getGrossDurationSeconds(),
                session.getPauseDurationSeconds(),
                session.getDiscardReason(),
                session.getVersion());
    }
}
