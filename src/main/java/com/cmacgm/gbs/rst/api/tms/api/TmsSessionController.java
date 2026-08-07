package com.cmacgm.gbs.rst.api.tms.api;

import java.time.LocalDate;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.tms.api.dto.DiscardTmsSessionRequest;
import com.cmacgm.gbs.rst.api.tms.api.dto.StartTmsSessionRequest;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSessionResponse;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSummaryResponse;
import com.cmacgm.gbs.rst.api.tms.application.TmsSessionCommandService;
import com.cmacgm.gbs.rst.api.tms.application.TmsSessionQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tms")
@PreAuthorize("hasRole('AGENT')")
public class TmsSessionController {

    private final TmsSessionCommandService commandService;
    private final TmsSessionQueryService queryService;

    public TmsSessionController(
            TmsSessionCommandService commandService,
            TmsSessionQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping("/summary")
    public TmsSummaryResponse summary(@AuthenticationPrincipal RstPrincipal principal) {
        return queryService.summary(principal.userId());
    }

    @GetMapping("/sessions/current")
    public TmsSessionResponse current(@AuthenticationPrincipal RstPrincipal principal) {
        return queryService.current(principal.userId());
    }

    @GetMapping("/sessions")
    public PageResponse<TmsSessionResponse> sessions(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return queryService.sessions(
                principal.userId(),
                status,
                query,
                dateFrom,
                dateTo,
                page,
                pageSize);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public TmsSessionResponse start(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody StartTmsSessionRequest request) {
        return commandService.start(principal.userId(), principal.ccgid(), request);
    }

    @PostMapping("/sessions/{id}/pause")
    public TmsSessionResponse pause(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable String id) {
        return commandService.pause(principal.userId(), id);
    }

    @PostMapping("/sessions/{id}/resume")
    public TmsSessionResponse resume(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable String id) {
        return commandService.resume(principal.userId(), id);
    }

    @PostMapping("/sessions/{id}/end")
    public TmsSessionResponse end(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable String id) {
        return commandService.end(principal.userId(), id);
    }

    @PostMapping("/sessions/{id}/discard")
    public TmsSessionResponse discard(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody DiscardTmsSessionRequest request) {
        return commandService.discard(principal.userId(), id, request.reason());
    }
}
