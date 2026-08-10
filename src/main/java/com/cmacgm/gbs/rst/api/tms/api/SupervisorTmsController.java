package com.cmacgm.gbs.rst.api.tms.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.TeamAgent;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSessionResponse;
import com.cmacgm.gbs.rst.api.tms.application.TmsSessionQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supervisor read-only TMS session browse within Timesheet / Toolkit scope.
 */
@RestController
@RequestMapping("/api/v1/supervisor/tms")
@PreAuthorize("hasRole('SUPERVISOR')")
public class SupervisorTmsController {

    private final TmsSessionQueryService queryService;

    /**
     * Creates the Supervisor TMS controller.
     *
     * @param queryService TMS query service
     */
    public SupervisorTmsController(TmsSessionQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Lists team agents available for TMS filters.
     *
     * @param principal authenticated Supervisor
     * @return team agents
     */
    @GetMapping("/agents")
    public List<TeamAgent> teamAgents(@AuthenticationPrincipal RstPrincipal principal) {
        return queryService.teamAgents(principal.ccgid());
    }

    /**
     * Lists TMS sessions for toolkits in the Supervisor scope.
     *
     * @param principal authenticated Supervisor
     * @param agentCcgid optional agent filter
     * @param toolkitId optional toolkit filter
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
    @GetMapping("/sessions")
    public PageResponse<TmsSessionResponse> sessions(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String agentCcgid,
            @RequestParam(required = false) UUID toolkitId,
            @RequestParam(required = false) String pl3Code,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sessionNo,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) String query,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return queryService.sessionsForSupervisor(
                principal.ccgid(),
                agentCcgid,
                toolkitId,
                pl3Code,
                status,
                sessionNo,
                reference,
                query,
                dateFrom,
                dateTo,
                page,
                pageSize);
    }

    /**
     * Returns a TMS session detail when it belongs to a scoped toolkit.
     *
     * @param principal authenticated Supervisor
     * @param id session number
     * @return session detail
     */
    @GetMapping("/sessions/{id}")
    public TmsSessionResponse get(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable String id) {
        return queryService.getForSupervisor(principal.ccgid(), id);
    }
}
