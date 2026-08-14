package com.cmacgm.gbs.rst.api.toolkit.api;

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
@RequestMapping("/api/v1/supervisor/toolkits")
@PreAuthorize("hasRole('SUPERVISOR')")
public class SupervisorToolkitController {

    private final ToolkitService toolkits;

    public SupervisorToolkitController(ToolkitService toolkits) {
        this.toolkits = toolkits;
    }

    @GetMapping
    public ToolkitListView managed(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String pl3Name) {
        return toolkits.supervisorToolkitList(principal.ccgid(), name, pl3Name);
    }

    @GetMapping("/{id}")
    public ToolkitResponse detail(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return toolkits.detail(principal.ccgid(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ToolkitResponse create(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody CreateToolkitRequest request) {
        return toolkits.create(principal.userId(), principal.ccgid(), request);
    }

    @PutMapping("/{id}")
    public ToolkitResponse update(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateToolkitRequest request) {
        return toolkits.update(principal.userId(), principal.ccgid(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        toolkits.delete(principal.ccgid(), id);
    }
}
