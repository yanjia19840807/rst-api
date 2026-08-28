package com.cmacgm.gbs.rst.api.supportcategory.api;

import java.util.List;

import com.cmacgm.gbs.rst.api.supportcategory.api.dto.SupportCategoryOption;
import com.cmacgm.gbs.rst.api.supportcategory.application.SupportCategoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Production Support Category lookup (database-maintained).
 */
@RestController
@RequestMapping("/api/v1/support-categories")
public class SupportCategoryController {

    private final SupportCategoryService service;

    public SupportCategoryController(SupportCategoryService service) {
        this.service = service;
    }

    /**
     * Active Standard Categories for Workload Registry and reports.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR','MANAGER','CDH','LTH','HO','ADMIN')")
    public SupportCategoryView list() {
        return new SupportCategoryView(service.listActive());
    }

    public record SupportCategoryView(List<SupportCategoryOption> categories) {
    }
}
