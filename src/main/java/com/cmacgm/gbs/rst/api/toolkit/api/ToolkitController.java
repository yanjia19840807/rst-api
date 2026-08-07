package com.cmacgm.gbs.rst.api.toolkit.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitRequests.Create;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitRequests.SharedKpi;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitRequests.Subtask;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitRequests.Update;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitCommandService;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitQueryService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/supervisor/toolkits")
public class ToolkitController {

    private final ToolkitQueryService toolkitQueryService;
    private final ToolkitCommandService commandService;

    public ToolkitController(
            ToolkitQueryService toolkitQueryService, ToolkitCommandService commandService) {
        this.toolkitQueryService = toolkitQueryService;
        this.commandService = commandService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<ToolkitResponse> managed(
            @AuthenticationPrincipal RstPrincipal principal) {
        return toolkitQueryService.supervisorToolkits(principal.ccgid());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ToolkitResponse detail(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return toolkitQueryService.detail(principal.ccgid(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ToolkitResponse create(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody Create request) {
        return commandService.create(principal.userId(), principal.ccgid(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ToolkitResponse update(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody Update request) {
        return commandService.update(principal.userId(), principal.ccgid(), id, request);
    }

    @PostMapping("/{id}/subtasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ToolkitResponse addSubtask(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody Subtask request) {
        return commandService.addSubtask(principal.ccgid(), id, request);
    }

    @DeleteMapping("/{id}/subtasks/{subtaskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public void deleteSubtask(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID subtaskId) {
        commandService.deleteSubtask(principal.ccgid(), id, subtaskId);
    }

    @PutMapping("/{id}/shared-kpis")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ToolkitResponse replaceSharedKpis(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody List<SharedKpi> request) {
        return commandService.replaceSharedKpis(principal.ccgid(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public void delete(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        commandService.delete(principal.ccgid(), id);
    }
}
