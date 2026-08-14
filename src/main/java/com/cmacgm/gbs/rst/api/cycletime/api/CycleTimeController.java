package com.cmacgm.gbs.rst.api.cycletime.api;

import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.cycletime.api.dto.ExerciseTmsSessionResponse;
import com.cmacgm.gbs.rst.api.cycletime.application.CycleTimeService;
import com.cmacgm.gbs.rst.api.cycletime.api.dto.BaselineFileView;
import com.cmacgm.gbs.rst.api.cycletime.api.dto.BaselineView;
import com.cmacgm.gbs.rst.api.cycletime.api.dto.ManualBaselineRequest;
import com.cmacgm.gbs.rst.api.cycletime.api.dto.PatchTmsSessionRequest;
import com.cmacgm.gbs.rst.api.cycletime.api.dto.PatchTmsSessionResult;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Supervisor Cycle Time endpoints (manual baseline, active read, Embedded TMS browse / exclusion).
 */
@RestController
@RequestMapping("/api/v1/supervisor/exercises/{exerciseId}/cycle-time")
@PreAuthorize("hasAnyRole('SUPERVISOR','MANAGER','CDH','LTH')")
public class CycleTimeController {

    private final CycleTimeService service;

    /**
     * Creates the Cycle Time controller.
     *
     * @param service Cycle Time service
     */
    public CycleTimeController(CycleTimeService service) {
        this.service = service;
    }

    /**
     * Creates an active MANUAL Cycle Time baseline.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param request manual baseline payload
     * @return created baseline
     */
    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public BaselineView createManual(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @Valid @RequestBody ManualBaselineRequest request) {
        return service.createManual(principal.ccgid(), exerciseId, request);
    }

    /**
     * Uploads a MANUAL median support-file stub for this Exercise.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param file multipart file
     * @return created file metadata
     */
    @PostMapping(value = "/support-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BaselineFileView uploadSupportFile(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @RequestParam("file") MultipartFile file) {
        return service.uploadSupportFile(principal.ccgid(), exerciseId, file);
    }

    /**
     * Returns the active Cycle Time baseline.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @return active baseline
     */
    @GetMapping("/active")
    public BaselineView getActive(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return service.getActive(principal.ccgid(), exerciseId);
    }

    /**
     * Lists TMS sessions linked to this Exercise for Embedded TMS review.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param page 1-based page
     * @param pageSize page size
     * @return paged session rows
     */
    @GetMapping("/sessions")
    public PageResponse<ExerciseTmsSessionResponse> listSessions(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return service.listTmsSessions(principal.ccgid(), exerciseId, page, pageSize);
    }

    /**
     * Includes or excludes a linked TMS session from the SYSTEM median population.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param sessionNo TMS session number
     * @param request inclusion flag (no reason)
     * @return updated session and active baseline
     */
    @PatchMapping("/sessions/{sessionNo}")
    public PatchTmsSessionResult patchSessionIncluded(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable String sessionNo,
            @Valid @RequestBody PatchTmsSessionRequest request) {
        return service.patchTmsSessionIncluded(
                principal.ccgid(), exerciseId, sessionNo, request);
    }
}
