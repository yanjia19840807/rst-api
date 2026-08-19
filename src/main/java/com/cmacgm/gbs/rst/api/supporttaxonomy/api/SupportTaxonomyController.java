package com.cmacgm.gbs.rst.api.supporttaxonomy.api;

import java.util.List;

import com.cmacgm.gbs.rst.api.supporttaxonomy.api.dto.SupportTaxonomyOption;
import com.cmacgm.gbs.rst.api.supporttaxonomy.application.SupportTaxonomyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Production Support Category lookup (database-maintained).
 */
@RestController
@RequestMapping("/api/v1/support-taxonomy")
public class SupportTaxonomyController {

    private final SupportTaxonomyService service;

    public SupportTaxonomyController(SupportTaxonomyService service) {
        this.service = service;
    }

    /**
     * Active Standard Categories for Workload Registry and reports.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR','MANAGER','CDH','LTH','HO')")
    public TaxonomyView list() {
        return new TaxonomyView(service.listActive());
    }

    public record TaxonomyView(List<SupportTaxonomyOption> categories) {
    }
}
