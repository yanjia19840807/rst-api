package com.cmacgm.gbs.rst.api.timesheet.api;

import java.time.LocalDate;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.security.RstPrincipal;
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
 * LTH Timesheet sync monitor and upload.
 */
@RestController
@RequestMapping("/api/v1/timesheet/sync")
@PreAuthorize("hasRole('LTH')")
public class TimesheetSyncController {

    private final TimesheetSyncAdminService admin;

    /**
     * @param admin monitor
     */
    public TimesheetSyncController(TimesheetSyncAdminService admin) {
        this.admin = admin;
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
    public RunDetail run(@PathVariable UUID id) {
        return admin.run(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RunHeader upload(
            @AuthenticationPrincipal RstPrincipal principal, @RequestParam("file") MultipartFile file) {
        return admin.upload(principal, file);
    }
}
