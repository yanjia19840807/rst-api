package com.cmacgm.gbs.rst.api.toolkit.api;

import java.util.List;

import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/toolkits")
public class AgentToolkitController {

    private final ToolkitQueryService toolkitQueryService;

    public AgentToolkitController(ToolkitQueryService toolkitQueryService) {
        this.toolkitQueryService = toolkitQueryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('AGENT')")
    public List<ToolkitResponse> list(@AuthenticationPrincipal RstPrincipal principal) {
        return toolkitQueryService.agentToolkits(principal.ccgid());
    }
}
