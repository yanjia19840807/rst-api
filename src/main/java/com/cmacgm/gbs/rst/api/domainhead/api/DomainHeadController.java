package com.cmacgm.gbs.rst.api.domainhead.api;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.domainhead.api.dto.DomainHeadPageView;
import com.cmacgm.gbs.rst.api.domainhead.api.dto.SaveDomainHeadsRequest;
import com.cmacgm.gbs.rst.api.domainhead.application.DomainHeadConfigService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LTH Domain Head configuration for the caller's Center.
 */
@RestController
@RequestMapping("/api/v1/domain-heads")
@PreAuthorize("hasRole('LTH')")
public class DomainHeadController {

    private final DomainHeadConfigService domainHeads;

    /**
     * Creates the Domain Head controller.
     *
     * @param domainHeads configuration service
     */
    public DomainHeadController(DomainHeadConfigService domainHeads) {
        this.domainHeads = domainHeads;
    }

    /**
     * Lists Domains in the LTH Center and the current CDH mapping.
     *
     * @param principal current LTH
     * @return page
     */
    @GetMapping
    public DomainHeadPageView page(@AuthenticationPrincipal RstPrincipal principal) {
        return domainHeads.page(principal);
    }

    /**
     * Saves dirty mappings and remounts READY CDH steps for changed Domains.
     *
     * @param principal current LTH
     * @param request dirty rows
     * @return updated page
     */
    @PutMapping
    public DomainHeadPageView save(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody SaveDomainHeadsRequest request) {
        return domainHeads.save(principal, request);
    }
}
