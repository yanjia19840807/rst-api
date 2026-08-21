package com.cmacgm.gbs.rst.api.associateddata.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.associateddata.application.AssociatedDataService;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.CalendarRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.CalendarView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.DailyVolumeRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.DailyVolumeView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.MonthlyVolumeRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.MonthlyVolumeView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ShiftRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ShiftView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SlotVolumeRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SlotVolumeView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SupportItemRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.SupportItemView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.TeamSetupRequest;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.TeamSetupView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ToolkitVolumePointsView;
import com.cmacgm.gbs.rst.api.associateddata.api.dto.ToolkitVolumeSummaryView;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
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

/**
 * Supervisor Associated Data endpoints under an Exercise.
 */
@RestController
@RequestMapping("/api/v1/supervisor/exercises/{exerciseId}")
@PreAuthorize("hasAnyRole('SUPERVISOR','MANAGER','CDH','LTH')")
public class ExerciseAssociatedDataController {

    private final AssociatedDataService service;

    /**
     * Creates the Associated Data controller.
     *
     * @param service Associated Data service
     */
    public ExerciseAssociatedDataController(AssociatedDataService service) {
        this.service = service;
    }

    /**
     * Returns Team Setup.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @return Team Setup
     */
    @GetMapping("/team-setup")
    public TeamSetupView getTeamSetup(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return service.getTeamSetup(principal.ccgid(), exerciseId);
    }

    /**
     * Replaces Team Setup inputs.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param request Team Setup payload
     * @return updated Team Setup
     */
    @PutMapping("/team-setup")
    public TeamSetupView putTeamSetup(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @RequestBody TeamSetupRequest request) {
        return service.putTeamSetup(principal.ccgid(), exerciseId, request);
    }

    /**
     * Lists shifts.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @return shifts
     */
    @GetMapping("/shifts")
    public List<ShiftView> getShifts(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return service.getShifts(principal.ccgid(), exerciseId);
    }

    /**
     * Replaces the full shift list.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param request shifts
     * @return active shifts
     */
    @PutMapping("/shifts")
    public List<ShiftView> putShifts(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @RequestBody List<@Valid ShiftRequest> request) {
        return service.putShifts(principal.ccgid(), exerciseId, request);
    }

    /**
     * Lists production support items.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @return support items
     */
    @GetMapping("/production-support")
    public List<SupportItemView> listSupport(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return service.listSupport(principal.ccgid(), exerciseId);
    }

    /**
     * Creates a production support item.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param request support payload
     * @return created item
     */
    @PostMapping("/production-support")
    @ResponseStatus(HttpStatus.CREATED)
    public SupportItemView createSupport(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @Valid @RequestBody SupportItemRequest request) {
        return service.createSupport(principal.ccgid(), exerciseId, request);
    }

    /**
     * Updates a production support item.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param itemId support item id
     * @param request support payload
     * @return updated item
     */
    @PutMapping("/production-support/{itemId}")
    public SupportItemView updateSupport(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID itemId,
            @Valid @RequestBody SupportItemRequest request) {
        return service.updateSupport(principal.ccgid(), exerciseId, itemId, request);
    }

    /**
     * Soft-deletes a production support item.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param itemId support item id
     */
    @DeleteMapping("/production-support/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSupport(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @PathVariable UUID itemId) {
        service.deleteSupport(principal.ccgid(), exerciseId, itemId);
    }

    /**
     * Returns calendar and holidays.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @return calendar
     */
    @GetMapping("/calendar")
    public CalendarView getCalendar(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return service.getCalendar(principal.ccgid(), exerciseId);
    }

    /**
     * Replaces holidays.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param request calendar payload
     * @return updated calendar
     */
    @PutMapping("/calendar")
    public CalendarView putCalendar(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @RequestBody CalendarRequest request) {
        return service.putCalendar(principal.ccgid(), exerciseId, request);
    }

    /**
     * Lists monthly volumes.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @return monthly volumes
     */
    @GetMapping("/volumes/monthly")
    public List<MonthlyVolumeView> getMonthly(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return service.getMonthlyVolumes(principal.ccgid(), exerciseId);
    }

    /**
     * Canonical Toolkit volume coverage (training source for later exercises).
     */
    @GetMapping("/volumes/toolkit-summary")
    public ToolkitVolumeSummaryView getToolkitVolumeSummary(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return service.getToolkitVolumeSummary(principal.ccgid(), exerciseId);
    }

    /**
     * Canonical Toolkit actuals used to pre-fill newly added or imported keys.
     */
    @GetMapping("/volumes/toolkit-points")
    public ToolkitVolumePointsView getToolkitVolumePoints(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return service.getToolkitVolumePoints(principal.ccgid(), exerciseId);
    }

    /**
     * Replaces monthly volumes.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param request monthly rows
     * @return replaced list
     */
    @PutMapping("/volumes/monthly")
    public List<MonthlyVolumeView> putMonthly(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @RequestBody List<MonthlyVolumeRequest> request) {
        return service.putMonthlyVolumes(principal.ccgid(), exerciseId, request);
    }

    /**
     * Lists daily volumes.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @return daily volumes
     */
    @GetMapping("/volumes/daily")
    public List<DailyVolumeView> getDaily(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return service.getDailyVolumes(principal.ccgid(), exerciseId);
    }

    /**
     * Replaces daily volumes.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param request daily rows
     * @return replaced list
     */
    @PutMapping("/volumes/daily")
    public List<DailyVolumeView> putDaily(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @RequestBody List<DailyVolumeRequest> request) {
        return service.putDailyVolumes(principal.ccgid(), exerciseId, request);
    }

    /**
     * Lists slot volumes.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @return slot volumes
     */
    @GetMapping("/volumes/slot")
    public List<SlotVolumeView> getSlot(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return service.getSlotVolumes(principal.ccgid(), exerciseId);
    }

    /**
     * Replaces slot volumes.
     *
     * @param principal authenticated Supervisor
     * @param exerciseId Exercise id
     * @param request slot rows
     * @return replaced list
     */
    @PutMapping("/volumes/slot")
    public List<SlotVolumeView> putSlot(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @RequestBody List<SlotVolumeRequest> request) {
        return service.putSlotVolumes(principal.ccgid(), exerciseId, request);
    }

    @GetMapping("/volumes/monthly/export-template")
    public ResponseEntity<byte[]> exportMonthlyTemplate(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return excelResponse(
                service.exportMonthlyTemplate(principal.ccgid(), exerciseId),
                "volume-monthly-template.xlsx");
    }

    @GetMapping("/volumes/monthly/export")
    public ResponseEntity<byte[]> exportMonthly(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return excelResponse(
                service.exportMonthlyExcel(principal.ccgid(), exerciseId),
                "volume-monthly.xlsx");
    }

    @PostMapping(value = "/volumes/monthly/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<MonthlyVolumeView> importMonthly(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @RequestParam("file") MultipartFile file) throws Exception {
        return service.importMonthlyExcel(
                principal.ccgid(), exerciseId, file.getInputStream(), file.getOriginalFilename());
    }

    @GetMapping("/volumes/daily/export-template")
    public ResponseEntity<byte[]> exportDailyTemplate(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return excelResponse(
                service.exportDailyTemplate(principal.ccgid(), exerciseId),
                "volume-daily-template.xlsx");
    }

    @GetMapping("/volumes/daily/export")
    public ResponseEntity<byte[]> exportDaily(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return excelResponse(
                service.exportDailyExcel(principal.ccgid(), exerciseId),
                "volume-daily.xlsx");
    }

    @PostMapping(value = "/volumes/daily/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<DailyVolumeView> importDaily(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @RequestParam("file") MultipartFile file) throws Exception {
        return service.importDailyExcel(
                principal.ccgid(), exerciseId, file.getInputStream(), file.getOriginalFilename());
    }

    @GetMapping("/volumes/slot/export-template")
    public ResponseEntity<byte[]> exportSlotTemplate(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return excelResponse(
                service.exportSlotTemplate(principal.ccgid(), exerciseId),
                "volume-slot-template.xlsx");
    }

    @GetMapping("/volumes/slot/export")
    public ResponseEntity<byte[]> exportSlot(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID exerciseId) {
        return excelResponse(
                service.exportSlotExcel(principal.ccgid(), exerciseId),
                "volume-slot.xlsx");
    }

    @PostMapping(value = "/volumes/slot/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<SlotVolumeView> importSlot(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID exerciseId,
            @RequestParam("file") MultipartFile file) throws Exception {
        return service.importSlotExcel(
                principal.ccgid(), exerciseId, file.getInputStream(), file.getOriginalFilename());
    }

    private static ResponseEntity<byte[]> excelResponse(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
