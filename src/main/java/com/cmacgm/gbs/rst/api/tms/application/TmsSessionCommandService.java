package com.cmacgm.gbs.rst.api.tms.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.cmacgm.gbs.rst.api.common.time.CenterZones;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.audit.api.dto.AuditActorView;
import com.cmacgm.gbs.rst.api.audit.application.AuditRecorder;
import com.cmacgm.gbs.rst.api.audit.domain.AuditAction;
import com.cmacgm.gbs.rst.api.audit.domain.AuditEntityType;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.delegation.application.PositionCoverage;
import com.cmacgm.gbs.rst.api.tms.api.dto.StartTmsSessionRequest;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSessionResponse;
import com.cmacgm.gbs.rst.api.tms.api.dto.UpdateTmsSessionRequest;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSubtask;
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
    private final PositionCoverage coverage;
    private final AuditRecorder audits;
    private final Clock clock;

    public TmsSessionCommandService(
            TmsSessionRepository sessionRepository,
            ToolkitRepository toolkitRepository,
            TimesheetReadService timesheet,
            PositionCoverage coverage,
            AuditRecorder audits,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.toolkitRepository = toolkitRepository;
        this.timesheet = timesheet;
        this.coverage = coverage;
        this.audits = audits;
        this.clock = clock;
    }

    @Transactional
    public TmsSessionResponse start(String agentCcgid, StartTmsSessionRequest request) {
        ensureNoActiveSession(agentCcgid);
        Toolkit toolkit = toolkitRepository.findActiveById(request.toolkitId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "toolkit-not-found",
                        "The Toolkit was not found."));
        if (!canUseToolkit(agentCcgid, toolkit)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "toolkit-out-of-scope",
                    "The Agent is not currently assigned to the Toolkit scope by Timesheet.");
        }
        var subtask = resolveSubtask(toolkit, request.subtaskId(), true);
        requireWholeVolume(request.processedVolume());
        ensureDocumentKeyAvailable(
                agentCcgid,
                toolkit.getId(),
                subtask == null ? null : subtask.getId(),
                normalize(request.reference()),
                null);

        var now = clock.instant();
        String positionId = coverage.positionId("AGENT");
        String storedAgent = storedAgentCcgid(agentCcgid, positionId);
        TmsSession session = TmsSession.start(
                nextSessionNumber(storedAgent == null ? agentCcgid : storedAgent, toolkit),
                storedAgent,
                positionId,
                toolkit,
                subtask,
                request.processedVolume(),
                normalize(request.reference()),
                normalize(request.remarks()),
                now);
        TmsSession saved = sessionRepository.saveAndFlush(session);
        saved.setLatestAuditEventId(
                audits.record(AuditEntityType.TMS_SESSION, saved.getId(), AuditAction.CREATE, "AGENT").getId());
        return toResponse(sessionRepository.saveAndFlush(saved), now);
    }

    @Transactional
    public TmsSessionResponse pause(
            String agentCcgid, String sessionNo, UpdateTmsSessionRequest request) {
        TmsSession session = ownedSession(agentCcgid, sessionNo);
        var now = clock.instant();
        applyDetails(session, request, now);
        session.pause(now);
        stamp(session, AuditAction.UPDATE);
        return toResponse(session, now);
    }

    @Transactional
    public TmsSessionResponse resume(String agentCcgid, String sessionNo) {
        TmsSession session = ownedSession(agentCcgid, sessionNo);
        var now = clock.instant();
        runningSession(agentCcgid)
                .filter(running -> !running.getSessionNo().equals(sessionNo))
                .ifPresent(running -> {
                    running.pause(now);
                    stamp(running, AuditAction.UPDATE);
                });
        session.resume(now);
        stamp(session, AuditAction.UPDATE);
        sessionRepository.flush();
        return toResponse(session, now);
    }

    @Transactional
    public TmsSessionResponse end(
            String agentCcgid, String sessionNo, UpdateTmsSessionRequest request) {
        TmsSession session = ownedSession(agentCcgid, sessionNo);
        var now = clock.instant();
        applyDetails(session, request, now);
        session.end(now);
        stamp(session, AuditAction.UPDATE);
        return toResponse(session, now);
    }

    @Transactional
    public TmsSessionResponse discard(String agentCcgid, String sessionNo, String reason) {
        TmsSession session = ownedSession(agentCcgid, sessionNo);
        var now = clock.instant();
        session.discard(reason == null ? "" : reason.trim(), now);
        stamp(session, AuditAction.DELETE);
        return toResponse(session, now);
    }

    @Transactional
    public TmsSessionResponse setEnabled(String supervisorCcgid, String sessionNo, boolean enabled) {
        TmsSession session = sessionRepository.findBySessionNo(sessionNo)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "tms-session-not-found",
                        "The TMS session was not found."));
        Toolkit toolkit = session.getToolkit();
        if (!timesheet.supervisorOwnsScope(
                supervisorCcgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code(), toolkit.getCenter())) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "tms-session-not-found",
                    "The TMS session was not found.");
        }
        var now = clock.instant();
        session.setEnabled(enabled, now);
        session.setLatestAuditEventId(audits.record(
                AuditEntityType.TMS_SESSION,
                session.getId(),
                enabled ? AuditAction.ENABLE : AuditAction.DISABLE,
                "SUPERVISOR").getId());
        return toResponse(session, now);
    }

    private void applyDetails(TmsSession session, UpdateTmsSessionRequest request, Instant now) {
        Toolkit toolkit = toolkitRepository.findExistingById(session.getToolkit().getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "toolkit-not-found",
                        "The Toolkit was not found."));
        ToolkitSubtask currentSubtask = session.getToolkitSubtask();
        UUID requestedSubtaskId = request == null
                ? (currentSubtask == null ? null : currentSubtask.getId())
                : request.subtaskId();
        ToolkitSubtask subtask = currentSubtask != null
                && requestedSubtaskId != null
                && requestedSubtaskId.equals(currentSubtask.getId())
                ? currentSubtask
                : resolveSubtask(toolkit, requestedSubtaskId, false);
        String reference = request == null ? session.getReference() : normalize(request.reference());
        ensureDocumentKeyAvailable(
                session.getAgentCcgid(),
                toolkit.getId(),
                subtask == null ? null : subtask.getId(),
                reference,
                session.getSessionNo());
        if (request == null) {
            return;
        }
        requireWholeVolume(request.processedVolume());
        session.updateDetails(
                subtask,
                request.processedVolume(),
                reference,
                normalize(request.remarks()),
                now);
    }

    private void ensureDocumentKeyAvailable(
            String agentCcgid,
            UUID toolkitId,
            UUID subtaskId,
            String reference,
            String excludeSessionNo) {
        String trimmed = normalize(reference);
        if (trimmed.isEmpty()) {
            return;
        }
        sessionRepository
                .findOccupyingDocumentKey(
                        agentCcgid, toolkitId, subtaskId, trimmed, TmsSessionStatus.DISCARDED)
                .stream()
                .filter(session -> excludeSessionNo == null || !session.getSessionNo().equals(excludeSessionNo))
                .findFirst()
                .ifPresent(existing -> {
                    throw documentKeyConflict(existing);
                });
    }

    private static ApiException documentKeyConflict(TmsSession existing) {
        if (existing.getStatus() == TmsSessionStatus.PAUSED) {
            return new ApiException(
                    HttpStatus.CONFLICT,
                    "document-session-exists",
                    "A paused session already exists for this Toolkit, TASK and Reference. Resume it, or discard it from Paused Sessions.");
        }
        if (existing.getStatus() == TmsSessionStatus.COMPLETED) {
            return new ApiException(
                    HttpStatus.CONFLICT,
                    "document-session-exists",
                    "A completed session already exists for this Toolkit, TASK and Reference.");
        }
        return new ApiException(
                HttpStatus.CONFLICT,
                "document-session-exists",
                "Another session already exists for this Toolkit, TASK and Reference.");
    }

    private ToolkitSubtask resolveSubtask(Toolkit toolkit, UUID subtaskId, boolean requireEnabled) {
        boolean hasSubtasks = !toolkit.getSubtasks().isEmpty();
        if (subtaskId == null) {
            if (hasSubtasks) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "subtask-required",
                        "Select a TASK. This Toolkit has at least one TASK.");
            }
            return null;
        }
        return toolkit.getSubtasks().stream()
                .filter(item -> item.getId().equals(subtaskId))
                .filter(item -> !requireEnabled || item.isEnabled())
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "invalid-subtask",
                        "The selected active Subtask does not belong to the Toolkit."));
    }

    private String storedAgentCcgid(String subjectCcgid, String positionId) {
        if (positionId == null) {
            return subjectCcgid;
        }
        var occupant = timesheet.occupant(positionId);
        if (occupant == null || occupant.ccgid() == null || occupant.ccgid().isBlank()
                || occupant.ccgid().equalsIgnoreCase(positionId)) {
            return null;
        }
        return occupant.ccgid();
    }

    private TmsSession ownedSession(String agentCcgid, String sessionNo) {
        Optional<TmsSession> byAgent = agentCcgid == null
                ? Optional.empty()
                : sessionRepository.findBySessionNoAndAgentCcgid(sessionNo, agentCcgid);
        if (byAgent.isPresent()) {
            return byAgent.get();
        }
        String positionId = coverage.positionId("AGENT");
        TmsSession session = sessionRepository.findBySessionNo(sessionNo)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "tms-session-not-found",
                        "The TMS session was not found."));
        if (positionId != null && positionId.equals(session.getPositionId())) {
            return session;
        }
        throw new ApiException(
                HttpStatus.NOT_FOUND,
                "tms-session-not-found",
                "The TMS session was not found.");
    }

    private boolean canUseToolkit(String agentCcgid, Toolkit toolkit) {
        if (timesheet.agentCanUse(
                agentCcgid,
                toolkit.getSupervisorPositionId(),
                toolkit.getPrimaryPl3Code(),
                toolkit.getCenter())) {
            return true;
        }
        return timesheet.agentPositionCanUse(
                coverage.positionId("AGENT"),
                toolkit.getSupervisorPositionId(),
                toolkit.getPrimaryPl3Code(),
                toolkit.getCenter());
    }

    private void stamp(TmsSession session, AuditAction action) {
        session.setLatestAuditEventId(
                audits.record(AuditEntityType.TMS_SESSION, session.getId(), action, "AGENT").getId());
        sessionRepository.save(session);
    }

    private Optional<TmsSession> runningSession(String agentCcgid) {
        Optional<TmsSession> byAgent = agentCcgid == null
                ? Optional.empty()
                : sessionRepository.findFirstByAgentCcgidAndStatusIn(
                        agentCcgid, Set.of(TmsSessionStatus.RUNNING));
        if (byAgent.isPresent()) {
            return byAgent;
        }
        String positionId = coverage.positionId("AGENT");
        if (positionId == null) {
            return Optional.empty();
        }
        return sessionRepository.findFirstByPositionIdAndStatusIn(
                positionId, Set.of(TmsSessionStatus.RUNNING));
    }

    private void ensureNoActiveSession(String agentCcgid) {
        runningSession(agentCcgid).ifPresent(session -> {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "active-session-exists",
                    "Session " + session.getSessionNo()
                            + " is still running. Pause or end it before starting another.");
        });
    }

    private String nextSessionNumber(String ccgid, Toolkit toolkit) {
        String owner = ccgid == null ? "AGENT" : ccgid.trim().toUpperCase(Locale.ROOT);
        ZoneId zone = CenterZones.of(toolkit.getCenter());
        String date = LocalDate.now(clock.withZone(zone)).format(SESSION_DATE);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "TMS-" + owner + "-" + date + "-" + suffix;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requireWholeVolume(BigDecimal volume) {
        if (!TmsSession.isWholeAtLeastOne(volume)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-volume",
                    "Volume must be a whole number of at least 1.");
        }
    }

    private TmsSessionResponse toResponse(TmsSession session, Instant now) {
        AuditActorView createdBy = audits.createdBy(AuditEntityType.TMS_SESSION, session.getId());
        return TmsSessionResponse.from(
                session,
                now,
                timesheet.displayNameByCcgid(session.getAgentCcgid()),
                createdBy,
                audits.actor(session.getLatestAuditEventId()));
    }
}
