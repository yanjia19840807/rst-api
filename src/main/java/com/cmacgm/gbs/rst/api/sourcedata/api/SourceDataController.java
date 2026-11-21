package com.cmacgm.gbs.rst.api.sourcedata.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.SourceDataAccess;
import com.cmacgm.gbs.rst.api.sourcedata.application.CenterSourceService;
import com.cmacgm.gbs.rst.api.sourcedata.application.CurrentDataService;
import com.cmacgm.gbs.rst.api.sourcedata.application.CurrentDataService.CurrentView;
import com.cmacgm.gbs.rst.api.sourcedata.application.CurrentDataService.HcView;
import com.cmacgm.gbs.rst.api.sourcedata.application.CurrentDataService.PersonView;
import com.cmacgm.gbs.rst.api.sourcedata.application.CurrentDataService.PositionView;
import com.cmacgm.gbs.rst.api.sourcedata.application.CurrentDataService.ProcessView;
import com.cmacgm.gbs.rst.api.sourcedata.application.CurrentDataService.SeatView;
import com.cmacgm.gbs.rst.api.sourcedata.application.CurrentDataService.SourceFileView;
import com.cmacgm.gbs.rst.api.sourcedata.application.GbsSiteCodeService;
import com.cmacgm.gbs.rst.api.sourcedata.application.SourceDataImportService;
import com.cmacgm.gbs.rst.api.sourcedata.application.SourceDataImportService.ImportResult;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterSource;
import com.cmacgm.gbs.rst.api.sourcedata.domain.GbsSiteCode;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.FileDownload;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * Upload MyHR and SKPI, and choose which centers use each source.
 */
@RestController
@RequestMapping("/api/v1/source-data")
@PreAuthorize("hasAnyRole('LOCAL_TRANSFORMATION_HEAD','ADMIN')")
public class SourceDataController {

    private final SourceDataImportService imports;
    private final CenterSourceService centers;
    private final GbsSiteCodeService siteCodes;
    private final CurrentDataService currentData;
    private final TimesheetSyncAdminService snapshots;

    /**
     * @param imports file import
     * @param centers center source mode
     * @param siteCodes site map
     * @param currentData mapped current data
     * @param snapshots original file download
     */
    public SourceDataController(
            SourceDataImportService imports,
            CenterSourceService centers,
            GbsSiteCodeService siteCodes,
            CurrentDataService currentData,
            TimesheetSyncAdminService snapshots) {
        this.imports = imports;
        this.centers = centers;
        this.siteCodes = siteCodes;
        this.currentData = currentData;
        this.snapshots = snapshots;
    }

    /**
     * @return center source modes
     */
    @GetMapping("/centers")
    public List<CenterView> centers(@AuthenticationPrincipal RstPrincipal principal) {
        if (!SourceDataAccess.centerScoped(principal)) {
            return centers.list().stream().map(CenterView::from).toList();
        }
        String own = SourceDataAccess.ownCenter(principal);
        return centers.list().stream()
                .filter(row -> own.equals(row.getCenter()))
                .map(CenterView::from)
                .toList();
    }

    /**
     * @param center GBS center
     * @param request new mode
     * @return saved mode
     */
    @PutMapping("/centers/{center}")
    public CenterView updateCenter(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable String center,
            @RequestBody ModeRequest request) {
        SourceDataAccess.requireGlobal(principal);
        return CenterView.from(centers.update(center, request == null ? null : request.mode()));
    }

    /**
     * @param file MyHR workbook
     * @return active snapshot
     */
    @PostMapping(value = "/myhr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult uploadMyHr(
            @AuthenticationPrincipal RstPrincipal principal, @RequestParam("file") MultipartFile file) {
        SourceDataAccess.requireGlobal(principal);
        return imports.importMyHr(fileName(file), bytes(file));
    }

    /**
     * @param file SKPI workbook
     * @return active snapshot
     */
    @PostMapping(value = "/skpi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult uploadSharedKpi(
            @AuthenticationPrincipal RstPrincipal principal, @RequestParam("file") MultipartFile file) {
        SourceDataAccess.requireGlobal(principal);
        return imports.importSharedKpi(fileName(file), bytes(file));
    }

    /**
     * @param id snapshot
     * @return original workbook
     */
    @GetMapping("/snapshots/{id}/file")
    public ResponseEntity<byte[]> file(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID id) {
        FileDownload file = snapshots.downloadSource(id, principal);
        String fileName = file.fileName().replace("\"", "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }

    /**
     * @return SKPI office mappings
     */
    @GetMapping("/sites")
    public List<SiteView> sites(@AuthenticationPrincipal RstPrincipal principal) {
        SourceDataAccess.requireGlobal(principal);
        return siteCodes.list().stream().map(SiteView::from).toList();
    }

    /**
     * @param request office, site code, and center
     * @return saved mapping
     */
    @PutMapping("/sites")
    public SiteView saveSite(
            @AuthenticationPrincipal RstPrincipal principal, @RequestBody SiteRequest request) {
        SourceDataAccess.requireGlobal(principal);
        if (request == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, TimesheetSyncErrorCode.INVALID_HEADER.code(), "Site mapping is required.");
        }
        return SiteView.from(siteCodes.save(request.gbsSite(), request.siteCode(), request.center()));
    }

    /**
     * @param gbsSite SKPI office
     */
    @DeleteMapping("/sites/{gbsSite}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSite(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable String gbsSite) {
        SourceDataAccess.requireGlobal(principal);
        siteCodes.delete(gbsSite);
    }

    /**
     * @return current MyHR and SKPI files
     */
    @GetMapping("/active")
    public List<SourceFileView> active(@AuthenticationPrincipal RstPrincipal principal) {
        SourceDataAccess.requireGlobal(principal);
        return currentData.activeInterim();
    }

    /**
     * @param center GBS center
     * @return mode and the files behind the current rows
     */
    @GetMapping("/current")
    public CurrentView current(
            @AuthenticationPrincipal RstPrincipal principal, @RequestParam String center) {
        CurrentView view = currentData.current(SourceDataAccess.visibleCenter(principal, center));
        return hideGlobalFiles(principal, view);
    }

    /**
     * @param center GBS center
     * @param q search text
     * @param page 1-based page
     * @param pageSize page size
     * @return MyHR people
     */
    @GetMapping("/current/people")
    public PageResponse<PersonView> people(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam String center,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return currentData.people(SourceDataAccess.visibleCenter(principal, center), q, page, pageSize);
    }

    /**
     * @param center GBS center
     * @param q search text
     * @param page 1-based page
     * @param pageSize page size
     * @return MyHR positions
     */
    @GetMapping("/current/positions")
    public PageResponse<PositionView> positions(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam String center,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return currentData.positions(SourceDataAccess.visibleCenter(principal, center), q, page, pageSize);
    }

    /**
     * @param center GBS center
     * @param q search text
     * @param page 1-based page
     * @param pageSize page size
     * @return MyHR seats
     */
    @GetMapping("/current/seats")
    public PageResponse<SeatView> seats(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam String center,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return currentData.seats(SourceDataAccess.visibleCenter(principal, center), q, page, pageSize);
    }

    /**
     * @param center GBS center
     * @param q search text
     * @param page 1-based page
     * @param pageSize page size
     * @return SKPI processes
     */
    @GetMapping("/current/processes")
    public PageResponse<ProcessView> processes(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam String center,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return currentData.processes(SourceDataAccess.visibleCenter(principal, center), q, page, pageSize);
    }

    /**
     * @param center GBS center
     * @param q search text
     * @param page 1-based page
     * @param pageSize page size
     * @return center Delivery HC
     */
    @GetMapping("/current/delivery-hc")
    public PageResponse<HcView> deliveryHc(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam String center,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return currentData.deliveryHc(SourceDataAccess.visibleCenter(principal, center), q, page, pageSize);
    }

    private static CurrentView hideGlobalFiles(RstPrincipal principal, CurrentView view) {
        if (!SourceDataAccess.centerScoped(principal)) {
            return view;
        }
        List<SourceFileView> files = view.files().stream()
                .map(file -> "MYHR".equals(file.kind()) || "SHARED_KPI".equals(file.kind())
                        ? new SourceFileView(null, file.kind(), null, null, null, null, false)
                        : file)
                .toList();
        return new CurrentView(view.center(), view.mode(), files);
    }

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, TimesheetSyncErrorCode.INVALID_HEADER.code(), "File is required.");
        }
        try {
            return file.getBytes();
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                    "Unable to read uploaded file.");
        }
    }

    private static String fileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "upload.xlsx" : name;
    }

    /**
     * @param center canonical center
     * @param mode TIMESHEET or MYHR_SKPI
     */
    public record CenterView(String center, String mode) {
        static CenterView from(CenterSource row) {
            return new CenterView(row.getCenter(), row.getSourceMode());
        }
    }

    /**
     * @param mode TIMESHEET or MYHR_SKPI
     */
    public record ModeRequest(String mode) {
    }

    /**
     * @param gbsSite SKPI office
     * @param siteCode 5-character site
     * @param center GBS center
     */
    public record SiteView(String gbsSite, String siteCode, String center) {
        static SiteView from(GbsSiteCode row) {
            return new SiteView(row.getGbsSite(), row.getSiteCode(), row.getCenter());
        }
    }

    /**
     * @param gbsSite SKPI office
     * @param siteCode 5-character site
     * @param center GBS center
     */
    public record SiteRequest(String gbsSite, String siteCode, String center) {
    }
}
