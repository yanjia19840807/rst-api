package com.cmacgm.gbs.rst.api.tms.api;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.delegation.application.PositionCoverage;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;

import com.cmacgm.gbs.rst.api.tms.api.dto.DiscardTmsSessionRequest;
import com.cmacgm.gbs.rst.api.tms.api.dto.PausedSessionMatchView;
import com.cmacgm.gbs.rst.api.tms.api.dto.StartTmsSessionRequest;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSessionResponse;
import com.cmacgm.gbs.rst.api.tms.api.dto.UpdateTmsSessionRequest;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSummaryResponse;
import com.cmacgm.gbs.rst.api.tms.application.TmsSessionCommandService;
import com.cmacgm.gbs.rst.api.tms.application.TmsSessionQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final PositionCoverage coverage;

    public TmsSessionController(
            TmsSessionCommandService commandService,
            TmsSessionQueryService queryService,
            PositionCoverage coverage) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.coverage = coverage;
    }

    private String agentCcgid(RstPrincipal principal) {
        return coverage.subjectCcgid(principal.ccgid(), "AGENT");
    }

    @GetMapping("/summary")
    public TmsSummaryResponse summary(@AuthenticationPrincipal RstPrincipal principal) {
        return queryService.summary(agentCcgid(principal), principal.center());
    }

    @GetMapping("/sessions/current")
    public TmsSessionResponse current(@AuthenticationPrincipal RstPrincipal principal) {
        return queryService.current(agentCcgid(principal));
    }

    @GetMapping("/sessions/paused-match")
    public PausedSessionMatchView pausedMatch(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam UUID toolkitId,
            @RequestParam(required = false) UUID subtaskId,
            @RequestParam(required = false) String reference) {
        return queryService.pausedMatch(agentCcgid(principal), toolkitId, subtaskId, reference);
    }

    @GetMapping("/sessions/export")
    public ResponseEntity<byte[]> exportSessions(
            @AuthenticationPrincipal RstPrincipal principal,
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
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) UUID toolkitId,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String pl3Code,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String site,
            @RequestParam(required = false) String customerCountry) {
        return excelResponse(
                queryService.exportSessions(
                        agentCcgid(principal),
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
                        principal.center()),
                "tms-sessions.xlsx");
    }

    @GetMapping("/sessions/{id}")
    public TmsSessionResponse get(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable String id) {
        return queryService.get(agentCcgid(principal), id);
    }

    @GetMapping("/sessions")
    public PageResponse<TmsSessionResponse> sessions(
            @AuthenticationPrincipal RstPrincipal principal,
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
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) UUID toolkitId,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String pl3Code,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String site,
            @RequestParam(required = false) String customerCountry,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return queryService.sessions(
                agentCcgid(principal),
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
                page,
                pageSize,
                principal.center());
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public TmsSessionResponse start(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody StartTmsSessionRequest request) {
        return commandService.start(agentCcgid(principal), request);
    }

    @PostMapping("/sessions/{id}/pause")
    public TmsSessionResponse pause(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody(required = false) UpdateTmsSessionRequest request) {
        return commandService.pause(agentCcgid(principal), id, request);
    }

    @PostMapping("/sessions/{id}/resume")
    public TmsSessionResponse resume(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable String id) {
        return commandService.resume(agentCcgid(principal), id);
    }

    @PostMapping("/sessions/{id}/end")
    public TmsSessionResponse end(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody(required = false) UpdateTmsSessionRequest request) {
        return commandService.end(agentCcgid(principal), id, request);
    }

    @PostMapping("/sessions/{id}/discard")
    public TmsSessionResponse discard(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable String id,
            @RequestBody(required = false) DiscardTmsSessionRequest request) {
        String reason = request == null ? null : request.reason();
        return commandService.discard(agentCcgid(principal), id, reason);
    }

    private static ResponseEntity<byte[]> excelResponse(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
