package com.cmacgm.gbs.rst.api.tms.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
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
    private final Clock clock;

    public TmsSessionQueryService(
            TmsSessionRepository sessionRepository,
            ToolkitService toolkits,
            TimesheetReadService timesheet,
            TmsSessionExcelService excel,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.toolkits = toolkits;
        this.timesheet = timesheet;
        this.excel = excel;
        this.clock = clock;
    }

    /**
     * Finds paused sessions for the same agent, Toolkit, and exact reference.
     * Blank references are ignored so empty invoices do not collide.
     *
     * @param agentCcgid current agent
     * @param toolkitId selected Toolkit
     * @param reference trimmed invoice / case id
     * @return latest match and how many paused rows share the key
     */
    @Transactional(readOnly = true)
    public PausedSessionMatchView pausedMatch(String agentCcgid, UUID toolkitId, String reference) {
        String trimmed = reference == null ? "" : reference.trim();
        if (toolkitId == null || trimmed.isEmpty()) {
            return new PausedSessionMatchView(null, 0);
        }
        List<TmsSession> matches =
                sessionRepository
                        .findByAgentCcgidAndStatusAndToolkit_IdAndReferenceIgnoreCaseOrderByPausedAtDescStartedAtDesc(
                                agentCcgid, TmsSessionStatus.PAUSED, toolkitId, trimmed);
        if (matches.isEmpty()) {
            return new PausedSessionMatchView(null, 0);
        }
        return new PausedSessionMatchView(toResponse(matches.get(0), clock.instant()), matches.size());
    }

    @Transactional(readOnly = true)
    public TmsSessionResponse current(String agentCcgid) {
        var now = clock.instant();
        return sessionRepository.findFirstByAgentCcgidAndStatusIn(
                        agentCcgid, Set.of(TmsSessionStatus.RUNNING))
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
            int page,
            int pageSize) {
        return pageSessions(
                new Filter(
                        agentCcgid,
                        null,
                        null,
                        null,
                        parseStatus(status),
                        sessionNo,
                        reference,
                        query,
                        dateFrom,
                        dateTo),
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
            int page,
            int pageSize) {
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
                dateTo), page, pageSize);
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
            LocalDate dateTo) {
        return excel.export(listSessions(new Filter(
                agentCcgid,
                null,
                null,
                null,
                parseStatus(status),
                sessionNo,
                reference,
                query,
                dateFrom,
                dateTo)));
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
            LocalDate dateTo) {
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
                dateTo)));
    }

    /**
     * Lists team agents under the current principal for TMS filters.
     */
    @Transactional(readOnly = true)
    public List<TeamAgent> teamAgents(String ccgid) {
        return timesheet.teamAgents(ccgid);
    }

    @Transactional(readOnly = true)
    public TmsSummaryResponse summary(String agentCcgid) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        var from = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new TmsSummaryResponse(
                sessionRepository.countByAgentCcgidAndStatusAndEndedAtGreaterThanEqualAndEndedAtLessThan(
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
            LocalDate dateTo) {
        Set<UUID> scopedToolkitIds = scopedToolkitIds(ccgid);
        if (toolkitId != null && !scopedToolkitIds.contains(toolkitId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "toolkit-out-of-scope",
                    "The Toolkit is outside the current Timesheet scope.");
        }

        String filterAgentCcgid = null;
        if (agentCcgid != null && !agentCcgid.isBlank()) {
            String trimmed = agentCcgid.trim();
            boolean onTeam = timesheet.teamAgents(ccgid).stream()
                    .anyMatch(agent -> agent.ccgid().equalsIgnoreCase(trimmed));
            if (!onTeam) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "agent-out-of-scope",
                        "The Agent is outside the current Timesheet team.");
            }
            filterAgentCcgid = trimmed;
        }

        return new Filter(
                filterAgentCcgid,
                scopedToolkitIds,
                toolkitId,
                pl3Code,
                parseStatus(status),
                sessionNo,
                reference,
                query,
                dateFrom,
                dateTo);
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
        return PageResponse.from(result, session -> toResponse(session, now, names));
    }

    private List<TmsSessionResponse> listSessions(Filter filter) {
        validateDateRange(filter);
        var now = clock.instant();
        Map<String, String> names = new HashMap<>();
        return sessionRepository
                .findAll(TmsSessionSpecification.filtered(filter), Sort.by(Sort.Direction.DESC, "startedAt"))
                .stream()
                .map(session -> toResponse(session, now, names))
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
        return toResponse(session, now, new HashMap<>());
    }

    private TmsSessionResponse toResponse(TmsSession session, Instant now, Map<String, String> names) {
        String agentName = names.computeIfAbsent(
                session.getAgentCcgid(), timesheet::displayNameByCcgid);
        return TmsSessionResponse.from(session, now, agentName);
    }

    private Set<UUID> scopedToolkitIds(String ccgid) {
        return toolkits.listManaged(ccgid).stream()
                .map(ToolkitResponse::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
