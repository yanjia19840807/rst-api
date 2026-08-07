package com.cmacgm.gbs.rst.api.timesheet.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.ActiveSnapshot;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.HierarchyCandidate;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService.KpiCandidate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
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
    public ActiveSnapshot active() {
        return service.activeSnapshot();
    }

    @GetMapping("/supervisor/hierarchy")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<HierarchyCandidate> hierarchy(
            @AuthenticationPrincipal RstPrincipal principal) {
        return service.supervisorHierarchy(principal.ccgid());
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
                        item.carrier(), item.site(), item.customerCountry(),
                        item.deliveryHc(), true))
                .toList();
        return new SharedKpiCandidates(
                service.activeSnapshot().syncDate(), countries, items);
    }

    @GetMapping("/countries")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<String> countries(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam String supervisorPositionId,
            @RequestParam String pl3Code) {
        requireSupervisorScope(principal, supervisorPositionId, pl3Code);
        return service.countries(supervisorPositionId, pl3Code);
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<KpiCandidate> kpis(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam String supervisorPositionId,
            @RequestParam String pl3Code,
            @RequestParam List<String> country) {
        requireSupervisorScope(principal, supervisorPositionId, pl3Code);
        return service.kpis(supervisorPositionId, pl3Code, country);
    }

    @GetMapping("/headcount")
    public BigDecimal headcount(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam String supervisorPositionId,
            @RequestParam String pl3Code,
            @RequestParam String carrier,
            @RequestParam String site,
            @RequestParam String country) {
        boolean allowed = principal.roles().contains("SUPERVISOR")
                ? service.supervisorOwnsScope(principal.ccgid(), supervisorPositionId, pl3Code)
                : service.agentCanUse(principal.ccgid(), supervisorPositionId, pl3Code);
        if (!allowed) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "timesheet-out-of-scope",
                    "The requested Timesheet scope is not available to the current principal.");
        }
        return service.headcount(supervisorPositionId, pl3Code, carrier, site, country);
    }

    private void requireSupervisorScope(
            RstPrincipal principal, String supervisorPositionId, String pl3Code) {
        if (!service.supervisorOwnsScope(principal.ccgid(), supervisorPositionId, pl3Code)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "timesheet-out-of-scope",
                    "The requested Timesheet scope is not owned by the current Supervisor.");
        }
    }

    public record SharedKpiCandidates(
            LocalDate syncDate, List<String> customerCountries, List<SharedKpiItem> items) {
    }

    public record SharedKpiItem(
            String carrier, String site, String customerCountry,
            BigDecimal deliveryHc, boolean valid) {
    }
}
