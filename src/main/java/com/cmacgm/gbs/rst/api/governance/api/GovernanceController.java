package com.cmacgm.gbs.rst.api.governance.api;

import java.time.LocalDate;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkingQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.BenchmarkingView;
import com.cmacgm.gbs.rst.api.governance.api.dto.DashboardView;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryListQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.RepositoryListView;
import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.SupportRepositoryView;
import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationWorkflowQuery;
import com.cmacgm.gbs.rst.api.governance.api.dto.ValidationWorkflowView;
import com.cmacgm.gbs.rst.api.governance.application.BenchmarkingService;
import com.cmacgm.gbs.rst.api.governance.application.DashboardService;
import com.cmacgm.gbs.rst.api.governance.application.RstRepositoryService;
import com.cmacgm.gbs.rst.api.governance.application.SupportRepositoryService;
import com.cmacgm.gbs.rst.api.governance.application.ValidationWorkflowService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Governance report endpoints for LTH / HO / ADMIN.
 */
@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceController {

    private final DashboardService dashboardService;
    private final RstRepositoryService rstRepository;
    private final SupportRepositoryService supportRepositoryService;
    private final ValidationWorkflowService validationWorkflowService;
    private final BenchmarkingService benchmarkingService;

    /**
     * Creates the governance controller.
     *
     * @param dashboardService completion, aging, and YTD capacity
     * @param rstRepository APPROVED Shared KPI repository
     * @param supportRepositoryService APPROVED Production Support repository
     * @param validationWorkflowService UNDER_REVIEW stuck-exercise list
     * @param benchmarkingService same-PL3 benchmarking
     */
    public GovernanceController(
            DashboardService dashboardService,
            RstRepositoryService rstRepository,
            SupportRepositoryService supportRepositoryService,
            ValidationWorkflowService validationWorkflowService,
            BenchmarkingService benchmarkingService) {
        this.dashboardService = dashboardService;
        this.rstRepository = rstRepository;
        this.supportRepositoryService = supportRepositoryService;
        this.validationWorkflowService = validationWorkflowService;
        this.benchmarkingService = benchmarkingService;
    }

    /**
     * Global dashboard metrics and center aging tables.
     *
     * @return dashboard payload
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('LTH','HO','ADMIN')")
    public DashboardView dashboard() {
        return dashboardService.build();
    }

    /**
     * RST repository Shared KPI rows for APPROVED Exercises, filtered on the server.
     *
     * @param exerciseCode optional exercise code contains
     * @param center optional exact GBS Center
     * @param domain optional exact domain
     * @param pl3Name optional exact PL3 name
     * @param toolkitName optional exact toolkit name
     * @param submittedFrom optional submitted date from
     * @param submittedTo optional submitted date to
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of filtered rows and unfiltered dropdown options
     */
    @GetMapping("/repository")
    @PreAuthorize("hasAnyRole('LTH','HO','ADMIN')")
    public RepositoryListView repository(
            @RequestParam(required = false) String exerciseCode,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String pl3Name,
            @RequestParam(required = false) String toolkitName,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return rstRepository.listApproved(
                new RepositoryListQuery(
                        exerciseCode,
                        center,
                        domain,
                        pl3Name,
                        toolkitName,
                        submittedFrom,
                        submittedTo),
                page,
                pageSize);
    }

    /**
     * Support repository activity rows for APPROVED Exercises, filtered on the server.
     * Totals and category mix follow the filtered rows; dropdown options do not shrink.
     *
     * @param center optional exact GBS Center
     * @param categoryId optional catalog Category id
     * @param toolkitName optional exact toolkit name
     * @param submittedFrom optional submitted date from
     * @param submittedTo optional submitted date to
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of filtered rows, summaries from all matches, and unfiltered dropdown options
     */
    @GetMapping("/support-repository")
    @PreAuthorize("hasAnyRole('LTH','HO','ADMIN')")
    public SupportRepositoryView supportRepository(
            @RequestParam(required = false) String center,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String toolkitName,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return supportRepositoryService.listApproved(
                new SupportRepositoryQuery(
                        center,
                        categoryId,
                        toolkitName,
                        submittedFrom,
                        submittedTo),
                page,
                pageSize);
    }

    /**
     * Same-PL3 benchmarking for APPROVED Shared KPI lines. Cards follow all filtered
     * matches; dropdown options do not shrink. Rows require {@code pl3Code}.
     *
     * @param center optional exact GBS Center
     * @param domain optional exact domain
     * @param pl1 optional exact PL1
     * @param pl2 optional exact PL2
     * @param pl3Code required exact PL3 code for like-for-like rows
     * @param submittedFrom optional submitted date from
     * @param submittedTo optional submitted date to
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of rows, cards from all matches, and unfiltered dropdown options
     */
    @GetMapping("/benchmarking")
    @PreAuthorize("hasAnyRole('LTH','HO','ADMIN')")
    public BenchmarkingView benchmarking(
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String pl1,
            @RequestParam(required = false) String pl2,
            @RequestParam(required = false) String pl3Code,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return benchmarkingService.listApproved(
                new BenchmarkingQuery(center, domain, pl1, pl2, pl3Code, submittedFrom, submittedTo),
                page,
                pageSize);
    }

    /**
     * UNDER_REVIEW Exercises waiting on Manager / CDH / LTH, filtered on the server.
     * Dropdown options do not shrink with the current filters.
     *
     * @param exerciseCode optional exercise code contains
     * @param center optional exact GBS Center
     * @param domain optional exact domain
     * @param pl3Name optional exact PL3 name
     * @param toolkitName optional exact toolkit name
     * @param agingMinDays optional minimum current-step wait in days
     * @param submittedFrom optional submitted date from
     * @param submittedTo optional submitted date to
     * @param page 1-based page
     * @param pageSize page size
     * @return one page of filtered rows and unfiltered dropdown options
     */
    @GetMapping("/validation-workflow")
    @PreAuthorize("hasAnyRole('LTH','ADMIN')")
    public ValidationWorkflowView validationWorkflow(
            @RequestParam(required = false) String exerciseCode,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String pl3Name,
            @RequestParam(required = false) String toolkitName,
            @RequestParam(required = false) Integer agingMinDays,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return validationWorkflowService.listUnderReview(
                new ValidationWorkflowQuery(
                        exerciseCode,
                        center,
                        domain,
                        pl3Name,
                        toolkitName,
                        agingMinDays,
                        submittedFrom,
                        submittedTo),
                page,
                pageSize);
    }
}
