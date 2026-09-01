package com.cmacgm.gbs.rst.api.domainhead.api;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Domain Head (CDH) configuration for an LTH Center, or any Center for ADMIN.
 */
@RestController
@RequestMapping("/api/v1/domain-heads")
@PreAuthorize("hasAnyRole('LTH', 'ADMIN')")
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
     * Distinct GBS centers from ACTIVE Daily people and Monthly scopes.
     *
     * @return centers for the Admin center picker
     */
    @GetMapping("/centers")
    public List<String> centers() {
        return domainHeads.availableCenters();
    }

    /**
     * Lists Domains in a Center and the current CDH mapping.
     *
     * @param principal current caller
     * @param center Admin-selected center; ignored for LTH
     * @return page
     */
    @GetMapping
    public DomainHeadPageView page(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String center) {
        return domainHeads.page(principal, center);
    }

    /**
     * Saves dirty mappings and remounts READY CDH steps for changed Domains.
     *
     * @param principal current caller
     * @param request dirty rows (and center for ADMIN)
     * @return updated page
     */
    @PutMapping
    public DomainHeadPageView save(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody SaveDomainHeadsRequest request) {
        return domainHeads.save(principal, request);
    }
}
