package com.cmacgm.gbs.rst.api.tms.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.tms.api.dto.StartTmsSessionRequest;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSessionResponse;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TmsSessionCommandService {

    private static final DateTimeFormatter SESSION_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final TmsSessionRepository sessionRepository;
    private final ToolkitRepository toolkitRepository;
    private final TimesheetReadService timesheet;
    private final Clock clock;

    public TmsSessionCommandService(
            TmsSessionRepository sessionRepository,
            ToolkitRepository toolkitRepository,
            TimesheetReadService timesheet,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.toolkitRepository = toolkitRepository;
        this.timesheet = timesheet;
        this.clock = clock;
    }

    @Transactional
    public TmsSessionResponse start(
            String agentCcgid, String agentDisplayName, StartTmsSessionRequest request) {
        ensureNoActiveSession(agentCcgid);
        Toolkit toolkit = toolkitRepository.findActiveById(request.toolkitId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "toolkit-not-found",
                        "The Toolkit was not found."));
        if (!timesheet.agentCanUse(
                agentCcgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "toolkit-out-of-scope",
                    "The Agent is not currently assigned to the Toolkit scope by Timesheet.");
        }
        var subtask = request.subtaskId() == null
                ? null
                : toolkit.getSubtasks().stream()
                        .filter(item -> item.getId().equals(request.subtaskId()))
                        .filter(item -> item.getDeletedAt() == null)
                        .findFirst()
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "invalid-subtask",
                                "The selected active Subtask does not belong to the Toolkit."));

        var now = clock.instant();
        String nameSnapshot = agentDisplayName == null || agentDisplayName.isBlank()
                ? agentCcgid
                : agentDisplayName.trim();
        TmsSession session = TmsSession.start(
                nextSessionNumber(agentCcgid),
                agentCcgid,
                nameSnapshot,
                toolkit,
                subtask,
                request.processedVolume(),
                normalize(request.reference()),
                normalize(request.remarks()),
                now);
        return TmsSessionResponse.from(sessionRepository.saveAndFlush(session), now);
    }

    @Transactional
    public TmsSessionResponse pause(String agentCcgid, String sessionNo) {
        TmsSession session = ownedSession(agentCcgid, sessionNo);
        var now = clock.instant();
        session.pause(now);
        return TmsSessionResponse.from(session, now);
    }

    @Transactional
    public TmsSessionResponse resume(String agentCcgid, String sessionNo) {
        TmsSession session = ownedSession(agentCcgid, sessionNo);
        ensureNoOtherActiveSession(agentCcgid, sessionNo);
        var now = clock.instant();
        session.resume(now);
        sessionRepository.flush();
        return TmsSessionResponse.from(session, now);
    }

    @Transactional
    public TmsSessionResponse end(String agentCcgid, String sessionNo) {
        TmsSession session = ownedSession(agentCcgid, sessionNo);
        var now = clock.instant();
        session.end(now);
        return TmsSessionResponse.from(session, now);
    }

    @Transactional
    public TmsSessionResponse discard(String agentCcgid, String sessionNo, String reason) {
        TmsSession session = ownedSession(agentCcgid, sessionNo);
        var now = clock.instant();
        session.discard(reason == null ? "" : reason.trim(), now);
        return TmsSessionResponse.from(session, now);
    }

    private TmsSession ownedSession(String agentCcgid, String sessionNo) {
        return sessionRepository.findBySessionNoAndAgentCcgid(sessionNo, agentCcgid)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "tms-session-not-found",
                        "The TMS session was not found."));
    }

    private void ensureNoActiveSession(String agentCcgid) {
        if (sessionRepository.existsByAgentCcgidAndStatusIn(
                agentCcgid, Set.of(TmsSessionStatus.RUNNING))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "active-session-exists",
                    "Pause or end the running session before starting another.");
        }
    }

    private void ensureNoOtherActiveSession(String agentCcgid, String currentSessionNo) {
        sessionRepository.findFirstByAgentCcgidAndStatusIn(
                        agentCcgid, Set.of(TmsSessionStatus.RUNNING))
                .filter(session -> !session.getSessionNo().equals(currentSessionNo))
                .ifPresent(session -> {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "active-session-exists",
                            "Pause or end the running session before resuming another.");
                });
    }

    private String nextSessionNumber(String ccgid) {
        String owner = ccgid == null ? "AGENT" : ccgid.trim().toUpperCase(Locale.ROOT);
        String date = LocalDate.now(clock.withZone(ZoneOffset.UTC)).format(SESSION_DATE);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "TMS-" + owner + "-" + date + "-" + suffix;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
