package com.cmacgm.gbs.rst.api.timesheet.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncAlert;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncAlertRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncIssueRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunSpecification;

/**
 * LTH monitor and manual upload.
 */
@Service
public class TimesheetSyncAdminService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_RECIPIENTS = 20;

    private final TimesheetSyncRunRepository syncRuns;
    private final TimesheetSyncIssueRepository issues;
    private final TimesheetSyncAlertRepository alerts;
    private final TimesheetSyncService syncService;
    private final Clock clock;

    /**
     * @param syncRuns run headers
     * @param issues issue rows
     * @param alerts alert config
     * @param syncService pipeline
     * @param clock timestamps
     */
    public TimesheetSyncAdminService(
            TimesheetSyncRunRepository syncRuns,
            TimesheetSyncIssueRepository issues,
            TimesheetSyncAlertRepository alerts,
            TimesheetSyncService syncService,
            Clock clock) {
        this.syncRuns = syncRuns;
        this.issues = issues;
        this.alerts = alerts;
        this.syncService = syncService;
        this.clock = clock;
    }

    /**
     * @param kind optional DAILY or MONTHLY
     * @param status optional run status
     * @param dateFrom inclusive sync date
     * @param dateTo inclusive sync date
     * @param page 1-based page
     * @param pageSize page size
     * @return ACTIVE snapshots and a page of recent runs
     */
    @Transactional(readOnly = true)
    public Overview overview(
            String kind, String status, LocalDate dateFrom, LocalDate dateTo, int page, int pageSize) {
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int safePage = Math.max(1, page);
        return new Overview(
                snapshot("DAILY").orElse(null),
                snapshot("MONTHLY").orElse(null),
                PageResponse.from(
                        syncRuns.findAll(
                                TimesheetSyncRunSpecification.filtered(kind, status, dateFrom, dateTo),
                                PageRequest.of(
                                        safePage - 1,
                                        safePageSize,
                                        Sort.by(Sort.Direction.DESC, "startedAt"))),
                        this::toHeader));
    }

    /**
     * @param id run
     * @param page 1-based page
     * @param pageSize page size
     * @return detail with a page of issues
     */
    @Transactional(readOnly = true)
    public RunDetail run(UUID id, int page, int pageSize) {
        TimesheetSyncRun run = syncRuns.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Timesheet sync run not found."));
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int safePage = Math.max(1, page);
        PageResponse<IssueView> issuePage = PageResponse.from(
                issues.findBySyncRunId(
                        id,
                        PageRequest.of(
                                safePage - 1,
                                safePageSize,
                                Sort.by(Sort.Direction.ASC, "sourceRow")
                                        .and(Sort.by(Sort.Direction.ASC, "createdAt")))),
                this::toIssue);
        if (issuePage.total() == 0 && run.getErrorCode() != null) {
            issuePage = PageResponse.ofList(List.of(runLevelIssue(run)), 1, safePageSize);
        }
        return new RunDetail(toHeader(run), issuePage);
    }

    private IssueView runLevelIssue(TimesheetSyncRun run) {
        return new IssueView(
                run.getId(),
                run.getErrorCode(),
                run.getErrorMessage() == null || run.getErrorMessage().isBlank()
                        ? "Timesheet sync failed."
                        : run.getErrorMessage(),
                null,
                null,
                null,
                null);
    }

    /**
     * @return current failure-alert config
     */
    @Transactional(readOnly = true)
    public AlertConfig alert() {
        TimesheetSyncAlert row = alerts.findById(TimesheetSyncAlert.SINGLETON_ID)
                .orElseGet(TimesheetSyncAlert::disabled);
        return new AlertConfig(row.isEnabled(), parseRecipients(row.getRecipients()));
    }

    /**
     * Replaces the failure-alert config.
     *
     * @param principal LTH
     * @param request new config
     * @return saved config
     */
    @Transactional
    public AlertConfig saveAlert(RstPrincipal principal, AlertConfig request) {
        AlertConfig incoming = request == null ? new AlertConfig(false, List.of()) : request;
        List<String> recipients = normalizeRecipients(incoming.recipients());
        if (incoming.enabled() && recipients.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ALERT",
                    "At least one recipient is required when email alerts are enabled.");
        }
        TimesheetSyncAlert row = alerts.findById(TimesheetSyncAlert.SINGLETON_ID)
                .orElseGet(TimesheetSyncAlert::disabled);
        row.replace(
                incoming.enabled(),
                String.join("\n", recipients),
                clock.instant(),
                principal == null ? "SYSTEM" : principal.ccgid());
        alerts.save(row);
        return new AlertConfig(row.isEnabled(), recipients);
    }

    /**
     * Stores the file on SharePoint Manual and syncs immediately.
     *
     * @param principal LTH
     * @param file upload
     * @return result header
     */
    public RunHeader upload(RstPrincipal principal, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, TimesheetSyncErrorCode.INVALID_HEADER.code(), "Timesheet file is required.");
        }
        String name = file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename();
        try {
            TimesheetSyncService.SyncResult result =
                    syncService.syncUploaded(name, file.getBytes(), principal == null ? "SYSTEM" : principal.ccgid());
            return toHeader(syncRuns.findById(result.id()).orElseThrow());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                    "Unable to read uploaded Timesheet file.");
        }
    }

    private Optional<RunHeader> snapshot(String kind) {
        return syncRuns.findByKindAndStatus(kind, "ACTIVE").map(this::toHeader);
    }

    private RunHeader toHeader(TimesheetSyncRun run) {
        return new RunHeader(
                run.getId(),
                run.getKind(),
                run.getStatus(),
                run.getSyncDate(),
                run.getAttemptNo(),
                run.getRowCount(),
                run.getSourceType(),
                run.getSourceFileName(),
                run.getSourceEtag(),
                run.getTriggeredByCcgid(),
                run.getErrorCode(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getCompletedAt());
    }

    private IssueView toIssue(TimesheetSyncIssue issue) {
        return new IssueView(
                issue.getId(),
                issue.getCode(),
                issue.getMessage(),
                issue.getEmpCcgid(),
                issue.getPositionId(),
                issue.getPl3Code(),
                issue.getSourceRow());
    }

    static List<String> parseRecipients(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> recipients = new ArrayList<>();
        for (String raw : stored.split("[,;\\n\\r]+")) {
            String email = raw.trim();
            if (email.isEmpty() || !EMAIL.matcher(email).matches()) {
                continue;
            }
            if (seen.add(email.toLowerCase(Locale.ROOT))) {
                recipients.add(email);
            }
        }
        return List.copyOf(recipients);
    }

    private static List<String> normalizeRecipients(List<String> incoming) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> recipients = new ArrayList<>();
        for (String raw : incoming == null ? List.<String>of() : incoming) {
            if (raw == null) {
                continue;
            }
            String email = raw.trim();
            if (email.isEmpty()) {
                continue;
            }
            if (!EMAIL.matcher(email).matches()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "INVALID_ALERT", "Invalid email address: " + email);
            }
            String key = email.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                recipients.add(email);
            }
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ALERT",
                    "At most " + MAX_RECIPIENTS + " recipients are allowed.");
        }
        return List.copyOf(recipients);
    }

    public record Overview(RunHeader daily, RunHeader monthly, PageResponse<RunHeader> runs) {
    }

    public record AlertConfig(boolean enabled, List<String> recipients) {
    }

    public record RunDetail(RunHeader run, PageResponse<IssueView> issues) {
    }

    public record RunHeader(
            UUID id,
            String kind,
            String status,
            LocalDate syncDate,
            short attemptNo,
            Integer rowCount,
            String sourceType,
            String sourceFileName,
            String sourceEtag,
            String triggeredByCcgid,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt) {
    }

    public record IssueView(
            UUID id,
            String code,
            String message,
            String empCcgid,
            String positionId,
            String pl3Code,
            Integer sourceRow) {
    }
}
