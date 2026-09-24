package com.cmacgm.gbs.rst.api.exercise.api;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;

import com.cmacgm.gbs.rst.api.exercise.api.dto.CommittedResultsStatus;
import com.cmacgm.gbs.rst.api.exercise.api.dto.CreateExerciseRequest;
import com.cmacgm.gbs.rst.api.exercise.api.dto.CreateExerciseResult;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseListQuery;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseListView;
import com.cmacgm.gbs.rst.api.exercise.api.dto.ExerciseResponse;
import com.cmacgm.gbs.rst.api.exercise.api.dto.UpdateExercisePeriodsRequest;
import com.cmacgm.gbs.rst.api.exercise.api.dto.UpdateExercisePeriodsResult;
import com.cmacgm.gbs.rst.api.exercise.api.dto.UpdateSlotPeriodRequest;
import com.cmacgm.gbs.rst.api.exercise.api.dto.UpdateSlotPeriodResult;
import com.cmacgm.gbs.rst.api.exercise.api.dto.UpdateTmsPeriodRequest;
import com.cmacgm.gbs.rst.api.exercise.application.ExerciseService;
import com.cmacgm.gbs.rst.api.delegation.application.PositionCoverage;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.exercise.submission.application.SubmissionService;
import com.cmacgm.gbs.rst.api.exercise.submission.application.SubmissionSummaryExcelService;
import com.cmacgm.gbs.rst.api.exercise.submission.application.SubmissionSummaryExcelService.ExportFile;
import com.cmacgm.gbs.rst.api.exercise.submission.api.dto.SubmitPreviewView;
import com.cmacgm.gbs.rst.api.exercise.submission.api.dto.SubmitRequest;
import com.cmacgm.gbs.rst.api.exercise.submission.api.dto.SubmittedDetailsView;
import org.springframework.format.annotation.DateTimeFormat;
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

/**
 * Exercise list/create/detail/delete and Submit endpoints.
 */
@RestController
@RequestMapping("/api/v1/exercises")
public class ExerciseController {

    private final ExerciseService service;
    private final SubmissionService submissions;
    private final SubmissionSummaryExcelService summaries;
    private final PositionCoverage coverage;

    /**
     * Creates the Exercise controller.
     *
     * @param service Exercise service
     * @param submissions Submission service
     * @param summaries Official Scenario Excel annex
     * @param coverage covered-position subject
     */
    public ExerciseController(
            ExerciseService service,
            SubmissionService submissions,
            SubmissionSummaryExcelService summaries,
            PositionCoverage coverage) {
        this.service = service;
        this.submissions = submissions;
        this.summaries = summaries;
        this.coverage = coverage;
    }

    private String supervisor(RstPrincipal principal) {
        return coverage.subjectCcgid(principal.ccgid(), "SUPERVISOR");
    }

    /**
     * Lists Exercises owned by the current principal, applying tab and field filters on the server.
     *
     * @param tab {@code IN_PROGRESS} or {@code VALIDATED} ({@code ARCHIVED} still accepted)
     * @param exerciseCode optional exercise code contains
     * @param toolkitName optional exact toolkit name
     * @param center optional exact GBS Center
     * @param domain optional exact Domain
     * @param pl3Name optional exact PL3 name
     * @param carrier optional exact Carrier on a frozen KPI line
     * @param site optional exact GBS Site on a frozen KPI line
     * @param customerCountry optional customer country token contained in a frozen KPI line
     * @param workflowStatus optional exact workflow status within the tab
     * @param reviewStage optional current step ({@code SUPERVISOR} / {@code MANAGER} / {@code CDH} / {@code LTH})
     * @param handler optional current reviewer display name (not CCGID)
     * @param officialScenario {@code ASSIGNED} or {@code UNASSIGNED}
     * @param sizingMonth optional exact sizing month ({@code YYYY-MM})
     * @param createdFrom optional created date from
     * @param createdTo optional created date to
     * @param submittedFrom optional submitted date from
     * @param submittedTo optional submitted date to
     * @param archivedFrom optional archived date from
     * @param archivedTo optional archived date to
     * @param page 1-based page
     * @param pageSize page size
     * @param principal authenticated owner
     * @return one page of filtered exercises and filter options
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ExerciseListView list(
            @RequestParam(required = false, defaultValue = "IN_PROGRESS") String tab,
            @RequestParam(required = false) String exerciseCode,
            @RequestParam(required = false) String toolkitName,
            @RequestParam(required = false) String center,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String pl3Name,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String site,
            @RequestParam(required = false) String customerCountry,
            @RequestParam(required = false) String workflowStatus,
            @RequestParam(required = false) String reviewStage,
            @RequestParam(required = false) String handler,
            @RequestParam(required = false) String officialScenario,
            @RequestParam(required = false) String sizingMonth,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate createdFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate createdTo,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate submittedTo,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate archivedFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate archivedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal RstPrincipal principal) {
        return service.list(supervisor(principal), new ExerciseListQuery(
                tab,
                exerciseCode,
                toolkitName,
                center,
                domain,
                pl3Name,
                carrier,
                site,
                customerCountry,
                workflowStatus,
                reviewStage,
                handler,
                officialScenario,
                sizingMonth,
                createdFrom,
                createdTo,
                submittedFrom,
                submittedTo,
                archivedFrom,
                archivedTo), page, pageSize);
    }

    /**
     * Creates an Exercise and seeds Associated Data from Toolkit latest state.
     *
     * @param principal authenticated owner
     * @param request create payload
     * @return created Exercise and initialization notices
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public CreateExerciseResult create(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody CreateExerciseRequest request) {
        return service.create(supervisor(principal), request);
    }

    /**
     * Returns Exercise detail including Official/submit flags.
     *
     * @param principal authenticated reader
     * @param id Exercise id
     * @return Exercise detail
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR','SR_MANAGER','DOMAIN_HEAD','LOCAL_TRANSFORMATION_HEAD')")
    public ExerciseResponse detail(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return service.detail(supervisor(principal), id);
    }

    /**
     * Soft-deletes an unsubmitted Exercise.
     *
     * @param principal authenticated owner
     * @param id Exercise id
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public void delete(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        service.softDelete(supervisor(principal), id);
    }

    /**
     * Updates Sizing Month on an editable Exercise.
     *
     * @param principal authenticated owner
     * @param id Exercise id
     * @param request period payload
     * @return updated Exercise and notices
     */
    @PutMapping("/{id}/periods")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public UpdateExercisePeriodsResult updatePeriods(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExercisePeriodsRequest request) {
        return service.updatePeriods(supervisor(principal), id, request);
    }

    /**
     * Sets TMS Period, links COMPLETED sessions, and refreshes the SYSTEM Cycle Time baseline.
     */
    @PutMapping("/{id}/tms-period")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public UpdateExercisePeriodsResult updateTmsPeriod(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTmsPeriodRequest request) {
        return service.updateTmsPeriod(supervisor(principal), id, request);
    }

    /**
     * Clears TMS Period, unlinks sessions, and drops the SYSTEM Cycle Time baseline.
     */
    @DeleteMapping("/{id}/tms-period")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public UpdateExercisePeriodsResult clearTmsPeriod(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID id) {
        return service.clearTmsPeriod(supervisor(principal), id);
    }

    /**
     * Sets Slot Period and rebuilds the empty Per-slot Volume grid.
     */
    @PutMapping("/{id}/slot-period")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public UpdateSlotPeriodResult updateSlotPeriod(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSlotPeriodRequest request) {
        return service.updateSlotPeriod(supervisor(principal), id, request);
    }

    /**
     * Clears Slot Period, the Per-slot Volume grid, and Slot Simulation only.
     */
    @DeleteMapping("/{id}/slot-period")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public UpdateSlotPeriodResult clearSlotPeriod(
            @AuthenticationPrincipal RstPrincipal principal, @PathVariable UUID id) {
        return service.clearSlotPeriod(supervisor(principal), id);
    }

    /**
     * Returns how many scenarios have saved Forecast / Simulation snapshots.
     */
    @GetMapping("/{id}/committed-results")
    @PreAuthorize("hasAnyRole('SUPERVISOR','SR_MANAGER','DOMAIN_HEAD','LOCAL_TRANSFORMATION_HEAD')")
    public CommittedResultsStatus committedResults(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return service.committedResults(supervisor(principal), id);
    }

    /**
     * Clears saved Forecast / Simulation snapshots. Scenario inputs and Official are kept.
     */
    @PostMapping("/{id}/committed-results/clear")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public CommittedResultsStatus clearCommittedResults(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return service.clearCommittedResults(supervisor(principal), id);
    }

    /**
     * Previews Submit validations for the current Official Package.
     *
     * @param principal authenticated owner
     * @param id Exercise id
     * @return submit preview
     */
    @PostMapping("/{id}/validations/submit-preview")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public SubmitPreviewView submitPreview(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return submissions.submitPreview(supervisor(principal), id);
    }

    /**
     * Submits the current Official Package for Manager validation.
     *
     * @param principal authenticated owner
     * @param id Exercise id
     * @param request submit payload
     * @return submitted details
     */
    @PostMapping("/{id}/submit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public SubmittedDetailsView submit(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) SubmitRequest request) {
        SubmitRequest payload = request == null ? new SubmitRequest(null, null) : request;
        return submissions.submit(principal, id, payload);
    }

    /**
     * Returns Submitted Details for a submitted Exercise.
     *
     * @param principal authenticated owner
     * @param id Exercise id
     * @return submitted details
     */
    @GetMapping("/{id}/submitted-details")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public SubmittedDetailsView submittedDetails(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return submissions.submittedDetails(supervisor(principal), id);
    }

    /**
     * Downloads the Official Scenario package as an Excel annex.
     */
    @GetMapping("/{id}/summary.xlsx")
    @PreAuthorize("hasAnyRole('SUPERVISOR','SR_MANAGER','DOMAIN_HEAD','LOCAL_TRANSFORMATION_HEAD')")
    public ResponseEntity<byte[]> downloadSummary(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        ExportFile file = summaries.export(supervisor(principal), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.body());
    }
}
