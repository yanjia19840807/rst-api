package com.cmacgm.gbs.rst.api.timesheet.api;

import java.time.LocalDate;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.AssignmentView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.KpiView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.PersonView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.PositionView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.ScopeView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.SnapshotFilters;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.AlertConfig;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.Overview;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.RunDetail;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.RunHeader;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * LTH / ADMIN Timesheet sync monitor and upload.
 */
@RestController
@RequestMapping("/api/v1/timesheet/sync")
@PreAuthorize("hasAnyRole('LTH','ADMIN')")
public class TimesheetSyncController {

    private final TimesheetSyncAdminService admin;
    private final TimesheetSnapshotBrowseService snapshots;

    /**
     * @param admin monitor
     * @param snapshots ACTIVE computed tables
     */
    public TimesheetSyncController(TimesheetSyncAdminService admin, TimesheetSnapshotBrowseService snapshots) {
        this.admin = admin;
        this.snapshots = snapshots;
    }

    @GetMapping
    public Overview overview(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return admin.overview(kind, status, dateFrom, dateTo, page, pageSize);
    }

    @GetMapping("/tables/filters")
    public SnapshotFilters tableFilters() {
        return snapshots.filters();
    }

    @GetMapping("/tables/people")
    public PageResponse<PersonView> people(
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return snapshots.people(center, q, page, pageSize);
    }

    @GetMapping("/tables/positions")
    public PageResponse<PositionView> positions(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return snapshots.positions(q, page, pageSize);
    }

    @GetMapping("/tables/scopes")
    public PageResponse<ScopeView> scopes(
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return snapshots.scopes(center, domain, q, page, pageSize);
    }

    @GetMapping("/tables/assignments")
    public PageResponse<AssignmentView> assignments(
            @RequestParam(required = false) String supervisorPositionId,
            @RequestParam(required = false) String pl3Code,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return snapshots.assignments(supervisorPositionId, pl3Code, q, page, pageSize);
    }

    @GetMapping("/tables/kpis")
    public PageResponse<KpiView> kpis(
            @RequestParam(required = false) String supervisorPositionId,
            @RequestParam(required = false) String pl3Code,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return snapshots.kpis(supervisorPositionId, pl3Code, q, page, pageSize);
    }

    @GetMapping("/alert")
    public AlertConfig alert() {
        return admin.alert();
    }

    @PutMapping("/alert")
    public AlertConfig saveAlert(
            @AuthenticationPrincipal RstPrincipal principal, @RequestBody AlertConfig request) {
        return admin.saveAlert(principal, request);
    }

    @GetMapping("/{id}")
    public RunDetail run(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return admin.run(id, page, pageSize);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RunHeader upload(
            @AuthenticationPrincipal RstPrincipal principal, @RequestParam("file") MultipartFile file) {
        return admin.upload(principal, file);
    }
}
