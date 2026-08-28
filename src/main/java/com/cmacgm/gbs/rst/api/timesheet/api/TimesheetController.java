package com.cmacgm.gbs.rst.api.timesheet.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.ActiveSnapshots;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.CenterPerson;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.HierarchyCandidate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timesheet")
@PreAuthorize("hasAnyRole('AGENT','SUPERVISOR')")
public class TimesheetController {

    private final TimesheetReadService service;

    public TimesheetController(TimesheetReadService service) {
        this.service = service;
    }

    @GetMapping("/active")
    public ActiveSnapshots active() {
        return service.activeSnapshots();
    }

    /**
     * Pages people in a Center who have a bindable position.
     *
     * @param principal current user
     * @param center optional Center; defaults to the identity Center
     * @param q optional name fragment
     * @param page 1-based page
     * @param pageSize page size
     * @return people
     */
    @GetMapping("/people")
    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','MANAGER','CDH','LTH','HO','ADMIN')")
    public PageResponse<TimesheetPersonView> people(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        String resolved = resolveCenter(principal, center);
        PageResponse<CenterPerson> people = service.peopleInCenter(resolved, q, page, pageSize);
        return new PageResponse<>(
                people.items().stream()
                        .map(person -> new TimesheetPersonView(
                                person.positionId(), person.ccgid(), person.name()))
                        .toList(),
                people.page(),
                people.pageSize(),
                people.total(),
                people.totalPages());
    }

    @GetMapping("/toolkit-hierarchy")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<HierarchyCandidate> toolkitHierarchy(
            @AuthenticationPrincipal RstPrincipal principal) {
        return service.supervisorHierarchy(principal.ccgid());
    }

    @GetMapping("/shared-kpi-candidates")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public SharedKpiCandidates sharedKpiCandidates(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam String pl3Code,
            @RequestParam(required = false) String supervisorPositionId,
            @RequestParam(name = "customerCountry", required = false)
                    List<String> customerCountries) {
        var positions = service.supervisorHierarchy(principal.ccgid()).stream()
                .filter(item -> item.pl3Code().equals(pl3Code))
                .map(HierarchyCandidate::supervisorPositionId)
                .distinct()
                .toList();
        String resolvedPosition = supervisorPositionId;
        if (resolvedPosition == null || resolvedPosition.isBlank()) {
            if (positions.size() != 1) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "ambiguous-supervisor-position",
                        "supervisorPositionId is required when PL3 belongs to multiple positions.");
            }
            resolvedPosition = positions.getFirst();
        }
        if (!positions.contains(resolvedPosition)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "timesheet-out-of-scope",
                    "The requested Timesheet scope is not owned by the current Supervisor.");
        }
        var countries = service.countries(resolvedPosition, pl3Code);
        var selected = customerCountries == null ? List.<String>of() : customerCountries;
        var items = service.kpis(resolvedPosition, pl3Code, selected).stream()
                .map(item -> new SharedKpiItem(
                        item.carrier(), item.site(), item.customerCountry(), item.deliveryHc()))
                .toList();
        return new SharedKpiCandidates(
                service.activeMonthly().syncDate(), countries, items);
    }

    public record SharedKpiCandidates(
            LocalDate syncDate, List<String> customerCountries, List<SharedKpiItem> items) {
    }

    public record SharedKpiItem(
            String carrier, String site, String customerCountry, BigDecimal deliveryHc) {
    }

    /**
     * One bindable person in a Center.
     *
     * @param positionId emp or occupied management position
     * @param ccgid identity
     * @param name display name
     */
    public record TimesheetPersonView(String positionId, String ccgid, String name) {
    }

    private static String resolveCenter(RstPrincipal principal, String requested) {
        String identity = principal == null || principal.center() == null || principal.center().isBlank()
                ? null
                : principal.center().trim();
        String query = requested == null || requested.isBlank() ? null : requested.trim();
        if (identity != null && query != null && !identity.equals(query)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "timesheet-center-forbidden",
                    "The requested Center is not the current identity Center.");
        }
        String center = query != null ? query : identity;
        if (center == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "identity-center-missing",
                    "Current identity has no Center.");
        }
        return center;
    }
}
