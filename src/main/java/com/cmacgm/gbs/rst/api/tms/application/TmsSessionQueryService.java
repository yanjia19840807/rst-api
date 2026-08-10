package com.cmacgm.gbs.rst.api.tms.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.identity.persistence.AppUserRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.TeamAgent;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSessionResponse;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSummaryResponse;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionRepository;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionSpecification;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionSpecification.Filter;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitResponse;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TmsSessionQueryService {

    private final TmsSessionRepository sessionRepository;
    private final ToolkitQueryService toolkitQueryService;
    private final TimesheetReadService timesheet;
    private final AppUserRepository users;
    private final Clock clock;

    public TmsSessionQueryService(
            TmsSessionRepository sessionRepository,
            ToolkitQueryService toolkitQueryService,
            TimesheetReadService timesheet,
            AppUserRepository users,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.toolkitQueryService = toolkitQueryService;
        this.timesheet = timesheet;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TmsSessionResponse current(UUID userId) {
        var now = clock.instant();
        return sessionRepository.findFirstByUserIdAndStatusIn(
                        userId, Set.of(TmsSessionStatus.RUNNING, TmsSessionStatus.PAUSED))
                .map(session -> TmsSessionResponse.from(session, now))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public TmsSessionResponse get(UUID userId, String sessionNo) {
        var now = clock.instant();
        return sessionRepository.findBySessionNoAndUserId(sessionNo, userId)
                .map(session -> TmsSessionResponse.from(session, now))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "tms-session-not-found",
                        "The TMS session was not found."));
    }

    /**
     * Returns a session visible to a Supervisor within their toolkit scope.
     *
     * @param supervisorCcgid supervisor CCGID
     * @param sessionNo session number
     * @return session detail
     */
    @Transactional(readOnly = true)
    public TmsSessionResponse getForSupervisor(String supervisorCcgid, String sessionNo) {
        Set<UUID> scopedToolkitIds = scopedToolkitIds(supervisorCcgid);
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
        return TmsSessionResponse.from(session, clock.instant());
    }

    @Transactional(readOnly = true)
    public PageResponse<TmsSessionResponse> sessions(
            UUID userId,
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
                        userId,
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
     * Lists completed/filtered TMS sessions for toolkits owned by the Supervisor.
     *
     * @param supervisorCcgid supervisor CCGID
     * @param agentCcgid optional team agent filter
     * @param toolkitId optional toolkit filter (must be in scope)
     * @param pl3Code optional PL3 code filter
     * @param status optional status
     * @param sessionNo optional session number contains
     * @param reference optional reference contains
     * @param query optional sessionNo∪reference contains
     * @param dateFrom optional start date from
     * @param dateTo optional start date to
     * @param page 1-based page
     * @param pageSize page size
     * @return paged sessions
     */
    @Transactional(readOnly = true)
    public PageResponse<TmsSessionResponse> sessionsForSupervisor(
            String supervisorCcgid,
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
        Set<UUID> scopedToolkitIds = scopedToolkitIds(supervisorCcgid);
        if (toolkitId != null && !scopedToolkitIds.contains(toolkitId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "toolkit-out-of-scope",
                    "The Toolkit is outside the current Timesheet scope.");
        }

        UUID agentUserId = null;
        if (agentCcgid != null && !agentCcgid.isBlank()) {
            String trimmed = agentCcgid.trim();
            boolean onTeam = timesheet.teamAgents(supervisorCcgid).stream()
                    .anyMatch(agent -> agent.ccgid().equalsIgnoreCase(trimmed));
            if (!onTeam) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "agent-out-of-scope",
                        "The Agent is outside the current Timesheet team.");
            }
            agentUserId = users.findByCcgidAndActiveTrue(trimmed)
                    .map(user -> user.getId())
                    .orElse(null);
            if (agentUserId == null) {
                return emptyPage(page, pageSize);
            }
        }

        return pageSessions(
                new Filter(
                        agentUserId,
                        scopedToolkitIds,
                        toolkitId,
                        pl3Code,
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
     * Lists team agents under the Supervisor for TMS filters.
     *
     * @param supervisorCcgid supervisor CCGID
     * @return team agents
     */
    @Transactional(readOnly = true)
    public List<TeamAgent> teamAgents(String supervisorCcgid) {
        return timesheet.teamAgents(supervisorCcgid);
    }

    @Transactional(readOnly = true)
    public TmsSummaryResponse summary(UUID userId) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        var from = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new TmsSummaryResponse(
                sessionRepository.countByUserIdAndStatusAndEndedAtGreaterThanEqualAndEndedAtLessThan(
                        userId,
                        TmsSessionStatus.COMPLETED,
                        from,
                        to),
                sessionRepository.sumVolume(
                        userId,
                        TmsSessionStatus.COMPLETED,
                        from,
                        to),
                sessionRepository.countByUserIdAndStatus(userId, TmsSessionStatus.PAUSED));
    }

    private PageResponse<TmsSessionResponse> pageSessions(Filter filter, int page, int pageSize) {
        if (filter.dateFrom() != null
                && filter.dateTo() != null
                && filter.dateFrom().isAfter(filter.dateTo())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-date-range",
                    "dateFrom cannot be after dateTo.");
        }
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        var pageable = PageRequest.of(
                safePage - 1,
                safePageSize,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = sessionRepository.findAll(TmsSessionSpecification.filtered(filter), pageable);
        var now = clock.instant();
        return PageResponse.from(result, session -> TmsSessionResponse.from(session, now));
    }

    private Set<UUID> scopedToolkitIds(String supervisorCcgid) {
        return toolkitQueryService.supervisorToolkits(supervisorCcgid).stream()
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
