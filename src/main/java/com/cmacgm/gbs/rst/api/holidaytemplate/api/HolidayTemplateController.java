package com.cmacgm.gbs.rst.api.holidaytemplate.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.CreateTemplateRequest;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.HolidayLineRequest;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.TemplateDetail;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.TemplateSummary;
import com.cmacgm.gbs.rst.api.holidaytemplate.application.HolidayTemplateService.UpdateTemplateRequest;
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
 * Center legal-holiday template management endpoints (LTH only).
 */
@RestController
@RequestMapping("/api/v1/holiday-templates")
@PreAuthorize("hasRole('LTH')")
public class HolidayTemplateController {

    private final HolidayTemplateService service;

    public HolidayTemplateController(HolidayTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<TemplateSummary> list(
            @RequestParam(required = false) String center,
            @RequestParam(required = false) Short year,
            @RequestParam(required = false) String status) {
        return service.list(center, year, status);
    }

    @GetMapping("/by-center")
    public TemplateDetail byCenter(
            @RequestParam String center,
            @RequestParam short year) {
        return service.findPublishedByCenterYear(center, year)
                .orElseThrow(() -> new com.cmacgm.gbs.rst.api.common.error.ApiException(
                        HttpStatus.NOT_FOUND,
                        "template-not-found",
                        "No published holiday template for this Center and year."));
    }

    @GetMapping("/export-blank")
    public ResponseEntity<byte[]> exportBlank() {
        return excelResponse(service.blankExcel(), "holiday-template-blank.xlsx");
    }

    @GetMapping("/{id}")
    public TemplateDetail detail(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateDetail create(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody CreateBody request) {
        return service.create(principal.ccgid(), request.toService());
    }

    @PutMapping("/{id}")
    public TemplateDetail update(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBody request) {
        return service.update(principal.ccgid(), id, request.toService());
    }

    @PostMapping("/{id}/publish")
    public TemplateDetail publish(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID id) {
        return service.publish(principal.ccgid(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID id) {
        service.softDelete(principal.ccgid(), id);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        TemplateDetail detail = service.get(id);
        String filename = "holiday-template-"
                + detail.center().replaceAll("\\s+", "-")
                + "-" + detail.year() + ".xlsx";
        return excelResponse(service.exportExcel(id), filename);
    }

    @PostMapping(path = "/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateDetail importExcel(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws Exception {
        return service.importExcel(principal.ccgid(), id, file.getInputStream());
    }

    private static ResponseEntity<byte[]> excelResponse(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    public record CreateBody(
            @NotBlank String center,
            @NotNull @Min(2000) @Max(2100) Short year,
            String defaultWeekendCode,
            String sourceNote,
            List<LineBody> holidays) {
        CreateTemplateRequest toService() {
            return new CreateTemplateRequest(
                    center,
                    year,
                    defaultWeekendCode,
                    sourceNote,
                    holidays == null ? List.of() : holidays.stream().map(LineBody::toService).toList());
        }
    }

    public record UpdateBody(
            String defaultWeekendCode,
            String sourceNote,
            List<LineBody> holidays) {
        UpdateTemplateRequest toService() {
            return new UpdateTemplateRequest(
                    defaultWeekendCode,
                    sourceNote,
                    holidays == null ? null : holidays.stream().map(LineBody::toService).toList());
        }
    }

    public record LineBody(
            @NotNull java.time.LocalDate holidayDate,
            @NotBlank String holidayName,
            Boolean workingDayOverride) {
        HolidayLineRequest toService() {
            return new HolidayLineRequest(holidayDate, holidayName, workingDayOverride);
        }
    }
}
