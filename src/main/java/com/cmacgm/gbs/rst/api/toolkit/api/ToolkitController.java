package com.cmacgm.gbs.rst.api.toolkit.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.CreateToolkitRequest;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitListView;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitResponse;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.UpdateToolkitRequest;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/toolkits")
public class ToolkitController {

    private final ToolkitService toolkits;

    public ToolkitController(ToolkitService toolkits) {
        this.toolkits = toolkits;
    }

    @GetMapping
    @PreAuthorize("hasRole('AGENT')")
    public List<ToolkitResponse> available(@AuthenticationPrincipal RstPrincipal principal) {
        return toolkits.listAvailable(principal.ccgid());
    }

    /**
     * Lists Toolkits the current principal can manage, filtered on the server.
     *
     * @param principal authenticated manager
     * @param name optional toolkit name contains
     * @param pl3Name optional exact PL3 name
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of toolkits and unfiltered PL3 options
     */
    @GetMapping("/managed")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ToolkitListView managed(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String pl3Name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return toolkits.listManaged(principal.ccgid(), name, pl3Name, page, pageSize);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR')")
    public ToolkitResponse detail(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return toolkits.detail(principal.ccgid(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ToolkitResponse create(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody CreateToolkitRequest request) {
        return toolkits.create(principal.ccgid(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ToolkitResponse update(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateToolkitRequest request) {
        return toolkits.update(principal.ccgid(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public void delete(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        toolkits.delete(principal.ccgid(), id);
    }
}
