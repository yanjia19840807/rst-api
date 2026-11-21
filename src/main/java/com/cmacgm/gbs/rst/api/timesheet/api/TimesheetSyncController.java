package com.cmacgm.gbs.rst.api.timesheet.api;

import java.time.LocalDate;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.FileDownload;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.SourceDataAccess;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.AssignmentView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.KpiView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.OccupancyView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.PersonView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.PositionView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.ScopeView;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSnapshotBrowseService.SnapshotFilters;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.AlertConfig;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.Overview;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.RunDetail;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.RunHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@PreAuthorize("hasAnyRole('LOCAL_TRANSFORMATION_HEAD','ADMIN')")
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
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return admin.overview(kind, status, center, sourceType, dateFrom, dateTo, page, pageSize, principal);
    }

    @GetMapping("/tables/filters")
    public SnapshotFilters tableFilters(@AuthenticationPrincipal RstPrincipal principal) {
        SnapshotFilters filters = snapshots.filters();
        if (!SourceDataAccess.centerScoped(principal)) {
            return filters;
        }
        String own = SourceDataAccess.ownCenter(principal);
        return new SnapshotFilters(
                filters.peopleCenters().stream().filter(own::equals).toList(),
                filters.scopeCenters().stream().filter(own::equals).toList(),
                filters.scopeDomains());
    }

    @GetMapping("/tables/people")
    public PageResponse<PersonView> people(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return snapshots.people(center(principal, center), q, page, pageSize);
    }

    @GetMapping("/tables/positions")
    public PageResponse<PositionView> positions(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return snapshots.positions(center(principal, center), q, page, pageSize);
    }

    @GetMapping("/tables/occupancies")
    public PageResponse<OccupancyView> occupancies(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return snapshots.occupancies(center(principal, center), q, page, pageSize);
    }

    @GetMapping("/tables/scopes")
    public PageResponse<ScopeView> scopes(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String supervisor,
            @RequestParam(required = false) String pl3Code,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return snapshots.scopes(center(principal, center), supervisor, pl3Code, page, pageSize);
    }

    @GetMapping("/tables/assignments")
    public PageResponse<AssignmentView> assignments(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String agent,
            @RequestParam(required = false) String supervisor,
            @RequestParam(required = false) String pl3Code,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return snapshots.assignments(center(principal, center), agent, supervisor, pl3Code, page, pageSize);
    }

    @GetMapping("/tables/kpis")
    public PageResponse<KpiView> kpis(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String supervisor,
            @RequestParam(required = false) String pl3Code,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return snapshots.kpis(center(principal, center), supervisor, pl3Code, page, pageSize);
    }

    @GetMapping("/alert")
    public AlertConfig alert(@AuthenticationPrincipal RstPrincipal principal) {
        SourceDataAccess.requireGlobal(principal);
        return admin.alert();
    }

    @PutMapping("/alert")
    public AlertConfig saveAlert(
            @AuthenticationPrincipal RstPrincipal principal, @RequestBody AlertConfig request) {
        SourceDataAccess.requireGlobal(principal);
        return admin.saveAlert(principal, request);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID id) {
        FileDownload file = admin.downloadSource(id, principal);
        String fileName = file.fileName().replace("\"", "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }

    @GetMapping("/{id}")
    public RunDetail run(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return admin.run(id, page, pageSize, principal);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RunHeader upload(
            @AuthenticationPrincipal RstPrincipal principal, @RequestParam("file") MultipartFile file) {
        return admin.upload(file, principal);
    }

    private static String center(RstPrincipal principal, String requested) {
        return SourceDataAccess.visibleCenter(principal, requested);
    }
}
