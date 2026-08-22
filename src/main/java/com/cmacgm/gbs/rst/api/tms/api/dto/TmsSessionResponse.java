package com.cmacgm.gbs.rst.api.tms.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSubtask;

public record TmsSessionResponse(
        String id,
        UUID toolkitId,
        UUID subtaskId,
        String toolkitName,
        String subtaskName,
        String agentName,
        String agentCcgid,
        String pl3Code,
        BigDecimal processedVolume,
        String reference,
        String remarks,
        String status,
        Instant startedAt,
        Instant pausedAt,
        Instant endedAt,
        long netDurationSeconds,
        String discardReason,
        long version) {

    public static TmsSessionResponse from(TmsSession session, Instant now, String agentName) {
        Instant wireStartedAt = session.getRunningSince() == null
                ? session.getStartedAt()
                : session.getRunningSince();
        long wireNetDuration = session.elapsedSeconds(now);
        Toolkit toolkit = session.getToolkit();
        ToolkitSubtask subtask = session.getToolkitSubtask();
        String resolvedAgent = agentName == null || agentName.isBlank()
                ? session.getAgentCcgid()
                : agentName;
        return new TmsSessionResponse(
                session.getSessionNo(),
                toolkit.getId(),
                subtask == null ? null : subtask.getId(),
                toolkit.getName(),
                subtask == null ? "—" : subtask.getName(),
                resolvedAgent,
                session.getAgentCcgid(),
                toolkit.getPrimaryPl3Code(),
                session.getProcessedVolume(),
                session.getReference(),
                session.getRemarks(),
                session.getStatus().name().toLowerCase(Locale.ROOT),
                wireStartedAt,
                session.getPausedAt(),
                session.getEndedAt(),
                wireNetDuration,
                session.getDiscardReason(),
                session.getVersion());
    }
}
