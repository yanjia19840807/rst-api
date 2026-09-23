package com.cmacgm.gbs.rst.api.tms.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.cmacgm.gbs.rst.api.common.time.CenterZones;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.audit.api.dto.AuditActorView;
import com.cmacgm.gbs.rst.api.audit.application.AuditRecorder;
import com.cmacgm.gbs.rst.api.audit.domain.AuditEntityType;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.delegation.application.PositionCoverage;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.TeamAgent;
import com.cmacgm.gbs.rst.api.tms.api.dto.PausedSessionMatchView;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSessionResponse;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSummaryResponse;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionRepository;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionSpecification;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionSpecification.Filter;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitResponse;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TmsSessionQueryService {

    private final TmsSessionRepository sessionRepository;
    private final ToolkitService toolkits;
    private final TimesheetReadService timesheet;
    private final TmsSessionExcelService excel;
    private final PositionCoverage coverage;
    private final AuditRecorder audits;
    private final Clock clock;

    public TmsSessionQueryService(
            TmsSessionRepository sessionRepository,
            ToolkitService toolkits,
            TimesheetReadService timesheet,
            TmsSessionExcelService excel,
            PositionCoverage coverage,
            AuditRecorder audits,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.toolkits = toolkits;
        this.timesheet = timesheet;
        this.excel = excel;
        this.coverage = coverage;
        this.audits = audits;
        this.clock = clock;
    }

    /**
     * Finds paused sessions for the same agent, Toolkit, TASK, and exact reference.
     * Blank references are ignored so empty invoices do not collide.
     *
     * @param agentCcgid current agent
     * @param toolkitId selected Toolkit
     * @param subtaskId selected TASK, or null when the Toolkit has none
     * @param reference trimmed invoice / case id
     * @return latest paused match and how many paused rows share the key
     */
    @Transactional(readOnly = true)
    public PausedSessionMatchView pausedMatch(
            String agentCcgid, UUID toolkitId, UUID subtaskId, String reference) {
        String trimmed = reference == null ? "" : reference.trim();
        if (toolkitId == null || trimmed.isEmpty()) {
            return new PausedSessionMatchView(null, 0);
        }
        List<TmsSession> matches = sessionRepository
                .findOccupyingDocumentKey(
                        agentCcgid, toolkitId, subtaskId, trimmed, TmsSessionStatus.DISCARDED)
                .stream()
                .filter(session -> session.getStatus() == TmsSessionStatus.PAUSED)
                .toList();
        if (matches.isEmpty()) {
            return new PausedSessionMatchView(null, 0);
        }
        return new PausedSessionMatchView(toResponse(matches.get(0), clock.instant()), matches.size());
    }

    @Transactional(readOnly = true)
    public TmsSessionResponse current(String agentCcgid) {
        var now = clock.instant();
        Optional<TmsSession> byAgent = sessionRepository.findFirstByAgentCcgidAndStatusIn(
                agentCcgid, Set.of(TmsSessionStatus.RUNNING));
        if (byAgent.isPresent()) {
            return toResponse(byAgent.get(), now);
        }
        String positionId = coverage.positionId("AGENT");
        if (positionId == null) {
            return null;
        }
        return sessionRepository.findFirstByPositionIdAndStatusIn(
                        positionId, Set.of(TmsSessionStatus.RUNNING))
                .map(session -> toResponse(session, now))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public TmsSessionResponse get(String agentCcgid, String sessionNo) {
        var now = clock.instant();
        return sessionRepository.findBySessionNoAndAgentCcgid(sessionNo, agentCcgid)
                .map(session -> toResponse(session, now))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "tms-session-not-found",
                        "The TMS session was not found."));
    }

    /**
     * Returns a session visible within the principal's managed toolkit scope.
     *
     * @param ccgid manager CCGID
     * @param sessionNo session number
     * @return session detail
     */
    @Transactional(readOnly = true)
    public TmsSessionResponse getForTeam(String ccgid, String sessionNo) {
        Set<UUID> scopedToolkitIds = scopedToolkitIds(ccgid);
        TmsSession session = sessionRepository.findBySessionNo(sessionNo)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "tms-session-not-found",
                        "The TMS session was not found."));
        if (!scopedToolkitIds.contains(session.getToolkit().getId())) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "tms-session-not-found",
                    "The TMS session was not found.");
        }
        return toResponse(session, clock.instant());
    }

    @Transactional(readOnly = true)
    public PageResponse<TmsSessionResponse> sessions(
            String agentCcgid,
            String status,
            String sessionNo,
            String reference,
            String query,
            LocalDate dateFrom,
            LocalDate dateTo,
            Boolean enabled,
            UUID toolkitId,
            String center,
            String domain,
            String pl3Code,
            String carrier,
            String site,
            String customerCountry,
            int page,
            int pageSize,
            String dateCenter) {
        return pageSessions(
                agentFilter(
                        agentCcgid,
                        status,
                        sessionNo,
                        reference,
                        query,
                        dateFrom,
                        dateTo,
                        enabled,
                        toolkitId,
                        center,
                        domain,
                        pl3Code,
                        carrier,
                        site,
                        customerCountry,
                        dateCenter),
                page,
                pageSize);
    }

    /**
     * Lists completed/filtered TMS sessions for toolkits in the managed scope.
     */
    @Transactional(readOnly = true)
    public PageResponse<TmsSessionResponse> sessionsForTeam(
            String ccgid,
            String agentCcgid,
            UUID toolkitId,
            String pl3Code,
            String status,
            String sessionNo,
            String reference,
            String query,
            LocalDate dateFrom,
            LocalDate dateTo,
            Boolean enabled,
            String center,
            String domain,
            String carrier,
            String site,
            String customerCountry,
            int page,
            int pageSize,
            String dateCenter) {
        return pageSessions(teamFilter(
                ccgid,
                agentCcgid,
                toolkitId,
                pl3Code,
                status,
                sessionNo,
                reference,
                query,
                dateFrom,
                dateTo,
                enabled,
                center,
                domain,
                carrier,
                site,
                customerCountry,
                dateCenter), page, pageSize);
    }

    /**
     * Exports the agent's filtered TMS sessions without pagination.
     */
    @Transactional(readOnly = true)
    public byte[] exportSessions(
            String agentCcgid,
            String status,
            String sessionNo,
            String reference,
            String query,
            LocalDate dateFrom,
            LocalDate dateTo,
            Boolean enabled,
            UUID toolkitId,
            String center,
            String domain,
            String pl3Code,
            String carrier,
            String site,
            String customerCountry,
            String dateCenter) {
        return excel.export(listSessions(agentFilter(
                agentCcgid,
                status,
                sessionNo,
                reference,
                query,
                dateFrom,
                dateTo,
                enabled,
                toolkitId,
                center,
                domain,
                pl3Code,
                carrier,
                site,
                customerCountry,
                dateCenter)));
    }

    /**
     * Exports team-scoped filtered TMS sessions without pagination.
     */
    @Transactional(readOnly = true)
    public byte[] exportSessionsForTeam(
            String ccgid,
            String agentCcgid,
            UUID toolkitId,
            String pl3Code,
            String status,
            String sessionNo,
            String reference,
            String query,
            LocalDate dateFrom,
            LocalDate dateTo,
            Boolean enabled,
            String center,
            String domain,
            String carrier,
            String site,
            String customerCountry,
            String dateCenter) {
        return excel.export(listSessions(teamFilter(
                ccgid,
                agentCcgid,
                toolkitId,
                pl3Code,
                status,
                sessionNo,
                reference,
                query,
                dateFrom,
                dateTo,
                enabled,
                center,
                domain,
                carrier,
                site,
                customerCountry,
                dateCenter)));
    }

    /**
     * Lists agents who have completed sessions in the managed Toolkit scope.
     */
    @Transactional(readOnly = true)
    public List<TeamAgent> teamAgents(String ccgid) {
        return sessionAgentCcgids(ccgid).stream()
                .map(agentCcgid -> {
                    String name = timesheet.displayNameByCcgid(agentCcgid);
                    return new TeamAgent(
                            agentCcgid,
                            name == null || name.isBlank() ? agentCcgid : name,
                            null);
                })
                .sorted((left, right) -> left.name().compareToIgnoreCase(right.name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TmsSummaryResponse summary(String agentCcgid, String dateCenter) {
        ZoneId zone = CenterZones.of(dateCenter);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        var from = today.atStartOfDay(zone).toInstant();
        var to = today.plusDays(1).atStartOfDay(zone).toInstant();
        return new TmsSummaryResponse(
                sessionRepository.countByAgentCcgidAndStatusAndEnabledTrueAndEndedAtGreaterThanEqualAndEndedAtLessThan(
                        agentCcgid,
                        TmsSessionStatus.COMPLETED,
                        from,
                        to),
                Optional.ofNullable(sessionRepository.sumVolume(
                        agentCcgid,
                        TmsSessionStatus.COMPLETED,
                        from,
                        to)).orElse(BigDecimal.ZERO),
                sessionRepository.countByAgentCcgidAndStatus(agentCcgid, TmsSessionStatus.PAUSED));
    }

    private Filter agentFilter(
            String agentCcgid,
            String status,
            String sessionNo,
            String reference,
            String query,
            LocalDate dateFrom,
            LocalDate dateTo,
            Boolean enabled,
            UUID toolkitId,
            String center,
            String domain,
            String pl3Code,
            String carrier,
            String site,
            String customerCountry,
            String dateCenter) {
        Collection<UUID> toolkitIds = null;
        if (TmsToolkitScope.hasScopeFilter(
                toolkitId, center, domain, pl3Code, carrier, site, customerCountry)) {
            toolkitIds = TmsToolkitScope.matchingIds(
                    toolkits.listAvailable(agentCcgid),
                    null,
                    toolkitId,
                    center,
                    domain,
                    pl3Code,
                    carrier,
                    site,
                    customerCountry);
        }
        return new Filter(
                agentCcgid,
                toolkitIds,
                toolkitId,
                pl3Code,
                parseStatus(status),
                sessionNo,
                reference,
                query,
                dateFrom,
                dateTo,
                enabled,
                dateCenter,
                center,
                domain,
                carrier,
                site,
                customerCountry,
                vacantPositionId());
    }

    private Filter teamFilter(
            String ccgid,
            String agentCcgid,
            UUID toolkitId,
            String pl3Code,
            String status,
            String sessionNo,
            String reference,
            String query,
            LocalDate dateFrom,
            LocalDate dateTo,
            Boolean enabled,
            String center,
            String domain,
            String carrier,
            String site,
            String customerCountry,
            String dateCenter) {
        List<ToolkitResponse> managed = toolkits.listManaged(ccgid);
        Set<UUID> scopedToolkitIds = managed.stream()
                .map(ToolkitResponse::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (toolkitId != null && !scopedToolkitIds.contains(toolkitId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "toolkit-out-of-scope",
                    "The Toolkit is outside the current Timesheet scope.");
        }

        String filterAgentCcgid = null;
        if (agentCcgid != null && !agentCcgid.isBlank()) {
            String trimmed = agentCcgid.trim();
            boolean inScope = sessionAgentCcgids(ccgid).stream()
                    .anyMatch(agent -> agent.equalsIgnoreCase(trimmed));
            if (!inScope) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "agent-out-of-scope",
                        "The Agent is outside the current TMS session scope.");
            }
            filterAgentCcgid = trimmed;
        }

        Collection<UUID> toolkitIds = scopedToolkitIds;
        if (TmsToolkitScope.hasScopeFilter(
                toolkitId, center, domain, pl3Code, carrier, site, customerCountry)) {
            toolkitIds = TmsToolkitScope.matchingIds(
                    managed,
                    scopedToolkitIds,
                    toolkitId,
                    center,
                    domain,
                    pl3Code,
                    carrier,
                    site,
                    customerCountry);
        }

        return new Filter(
                filterAgentCcgid,
                toolkitIds,
                toolkitId,
                pl3Code,
                parseStatus(status),
                sessionNo,
                reference,
                query,
                dateFrom,
                dateTo,
                enabled,
                dateCenter,
                center,
                domain,
                carrier,
                site,
                customerCountry,
                null);
    }

    private String vacantPositionId() {
        String positionId = coverage.positionId("AGENT");
        if (positionId == null) {
            return null;
        }
        var occupant = timesheet.occupant(positionId);
        if (occupant == null || occupant.ccgid() == null || occupant.ccgid().isBlank()
                || occupant.ccgid().equalsIgnoreCase(positionId)) {
            return positionId;
        }
        return null;
    }

    private PageResponse<TmsSessionResponse> pageSessions(Filter filter, int page, int pageSize) {
        validateDateRange(filter);
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        var pageable = PageRequest.of(
                safePage - 1,
                safePageSize,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = sessionRepository.findAll(TmsSessionSpecification.filtered(filter), pageable);
        var now = clock.instant();
        Map<String, String> names = new HashMap<>();
        Map<UUID, AuditActorView> created = audits.createdBy(
                AuditEntityType.TMS_SESSION,
                result.getContent().stream().map(TmsSession::getId).toList());
        Map<UUID, AuditActorView> updated = audits.actors(
                result.getContent().stream().map(TmsSession::getLatestAuditEventId).toList());
        return PageResponse.from(result, session -> toResponse(session, now, names, created, updated));
    }

    private List<TmsSessionResponse> listSessions(Filter filter) {
        validateDateRange(filter);
        var now = clock.instant();
        Map<String, String> names = new HashMap<>();
        List<TmsSession> sessions = sessionRepository
                .findAll(TmsSessionSpecification.filtered(filter), Sort.by(Sort.Direction.DESC, "startedAt"));
        Map<UUID, AuditActorView> created = audits.createdBy(
                AuditEntityType.TMS_SESSION,
                sessions.stream().map(TmsSession::getId).toList());
        Map<UUID, AuditActorView> updated = audits.actors(
                sessions.stream().map(TmsSession::getLatestAuditEventId).toList());
        return sessions.stream()
                .map(session -> toResponse(session, now, names, created, updated))
                .toList();
    }

    private static void validateDateRange(Filter filter) {
        if (filter.dateFrom() != null
                && filter.dateTo() != null
                && filter.dateFrom().isAfter(filter.dateTo())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-date-range",
                    "dateFrom cannot be after dateTo.");
        }
    }

    private TmsSessionResponse toResponse(TmsSession session, Instant now) {
        return toResponse(session, now, new HashMap<>(), Map.of(), Map.of());
    }

    private TmsSessionResponse toResponse(
            TmsSession session,
            Instant now,
            Map<String, String> names,
            Map<UUID, AuditActorView> created,
            Map<UUID, AuditActorView> updated) {
        String agentName = names.computeIfAbsent(
                session.getAgentCcgid(), timesheet::displayNameByCcgid);
        AuditActorView createdBy = created.get(session.getId());
        if (createdBy == null && created.isEmpty()) {
            createdBy = audits.createdBy(AuditEntityType.TMS_SESSION, session.getId());
        }
        AuditActorView updatedBy = null;
        if (session.getLatestAuditEventId() != null) {
            updatedBy = updated.get(session.getLatestAuditEventId());
            if (updatedBy == null && updated.isEmpty()) {
                updatedBy = audits.actor(session.getLatestAuditEventId());
            }
        }
        return TmsSessionResponse.from(session, now, agentName, createdBy, updatedBy);
    }

    private Set<UUID> scopedToolkitIds(String ccgid) {
        return toolkits.listManaged(ccgid).stream()
                .map(ToolkitResponse::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<String> sessionAgentCcgids(String ccgid) {
        Set<UUID> toolkitIds = scopedToolkitIds(ccgid);
        if (toolkitIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(sessionRepository.findDistinctAgentCcgids(
                toolkitIds, TmsSessionStatus.COMPLETED));
    }

    private static PageResponse<TmsSessionResponse> emptyPage(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        return new PageResponse<>(List.of(), safePage, safePageSize, 0, 1);
    }

    private static TmsSessionStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TmsSessionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-session-status",
                    "Unsupported TMS session status: " + value);
        }
    }
}
