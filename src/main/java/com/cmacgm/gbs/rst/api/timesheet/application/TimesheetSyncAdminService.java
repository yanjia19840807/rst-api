package com.cmacgm.gbs.rst.api.timesheet.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cmacgm.gbs.rst.api.audit.api.dto.AuditActorView;
import com.cmacgm.gbs.rst.api.audit.application.AuditRecorder;
import com.cmacgm.gbs.rst.api.audit.domain.AuditEntityType;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
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
    private static final Pattern MISSING_FIELD = Pattern.compile("(?i)^Missing (.+)\\.$");
    private static final int MAX_RECIPIENTS = 20;

    private final TimesheetSyncRunRepository syncRuns;
    private final TimesheetSyncIssueRepository issues;
    private final TimesheetSyncAlertRepository alerts;
    private final TimesheetSyncService syncService;
    private final AuditRecorder audits;
    private final MicrosoftGraphService graph;
    private final Clock clock;

    /**
     * @param syncRuns run headers
     * @param issues issue rows
     * @param alerts alert config
     * @param syncService pipeline
     * @param audits who triggered each run
     * @param graph source file download
     * @param clock timestamps
     */
    public TimesheetSyncAdminService(
            TimesheetSyncRunRepository syncRuns,
            TimesheetSyncIssueRepository issues,
            TimesheetSyncAlertRepository alerts,
            TimesheetSyncService syncService,
            AuditRecorder audits,
            MicrosoftGraphService graph,
            Clock clock) {
        this.syncRuns = syncRuns;
        this.issues = issues;
        this.alerts = alerts;
        this.syncService = syncService;
        this.audits = audits;
        this.graph = graph;
        this.clock = clock;
    }

    /**
     * @param kind optional DAILY or MONTHLY
     * @param status optional run status
     * @param center optional GBS center
     * @param sourceType optional SHAREPOINT or MANUAL
     * @param dateFrom inclusive sync date
     * @param dateTo inclusive sync date
     * @param page 1-based page
     * @param pageSize page size
     * @return ACTIVE snapshots and a page of recent runs
     */
    @Transactional(readOnly = true)
    public Overview overview(
            String kind,
            String status,
            String center,
            String sourceType,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int pageSize) {
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int safePage = Math.max(1, page);
        Page<TimesheetSyncRun> runs = syncRuns.findAll(
                TimesheetSyncRunSpecification.filtered(kind, status, center, sourceType, dateFrom, dateTo),
                PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "startedAt")));
        return new Overview(snapshots("DAILY"), snapshots("MONTHLY"), headers(runs));
    }

    /**
     * Streams the source file stored on SharePoint for this run.
     *
     * @param id run
     * @return file name and bytes
     */
    @Transactional(readOnly = true)
    public FileDownload downloadSource(UUID id) {
        TimesheetSyncRun run = syncRuns.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Timesheet sync run not found."));
        String itemId = run.getSourceDriveItemId();
        if (itemId == null || itemId.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_UNAVAILABLE", "File is no longer available.");
        }
        try {
            String fileName = run.getSourceFileName() == null || run.getSourceFileName().isBlank()
                    ? "timesheet.xlsx"
                    : run.getSourceFileName();
            return new FileDownload(fileName, graph.getDriveItemBytesById(itemId));
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_UNAVAILABLE", "File is no longer available.");
        }
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
        List<IssueView> merged = mergeBySourceRow(issues.findBySyncRunIdOrderBySourceRowAscCreatedAtAsc(id));
        PageResponse<IssueView> issuePage = PageResponse.ofList(merged, safePage, safePageSize);
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
     * Stores the file on SharePoint Manual/Timesheet and syncs immediately.
     * The signed-in user is recorded on the sync audit event.
     *
     * @param file upload
     * @return result header
     */
    public RunHeader upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, TimesheetSyncErrorCode.INVALID_HEADER.code(), "Timesheet file is required.");
        }
        String name = file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename();
        try {
            TimesheetSyncService.SyncResult result = syncService.syncUploaded(name, file.getBytes());
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

    private List<RunHeader> snapshots(String kind) {
        List<TimesheetSyncRun> rows = syncRuns.findByKindAndStatus(kind, "ACTIVE");
        Map<UUID, AuditActorView> created = createdBy(rows);
        return rows.stream().map(run -> toHeader(run, created.get(run.getId()))).toList();
    }

    private PageResponse<RunHeader> headers(Page<TimesheetSyncRun> page) {
        Map<UUID, AuditActorView> created = createdBy(page.getContent());
        return PageResponse.from(page, run -> toHeader(run, created.get(run.getId())));
    }

    private Map<UUID, AuditActorView> createdBy(List<TimesheetSyncRun> rows) {
        return audits.createdBy(
                AuditEntityType.TIMESHEET_SYNC,
                rows.stream().map(TimesheetSyncRun::getId).toList());
    }

    private RunHeader toHeader(TimesheetSyncRun run) {
        return toHeader(run, audits.createdBy(AuditEntityType.TIMESHEET_SYNC, run.getId()));
    }

    private RunHeader toHeader(TimesheetSyncRun run, AuditActorView createdBy) {
        String itemId = run.getSourceDriveItemId();
        return new RunHeader(
                run.getId(),
                run.getKind(),
                run.getStatus(),
                run.getSyncDate(),
                run.getCenter(),
                run.getAttemptNo(),
                run.getRowCount(),
                run.getSourceType(),
                run.getSourceFileName(),
                run.getSourceEtag(),
                itemId != null && !itemId.isBlank(),
                createdBy,
                run.getErrorCode(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getCompletedAt());
    }

    /**
     * One display row per Excel source row. File-level issues stay separate.
     *
     * @param rows persisted issues in source-row order
     * @return merged views
     */
    static List<IssueView> mergeBySourceRow(List<TimesheetSyncIssue> rows) {
        List<IssueView> merged = new ArrayList<>();
        List<TimesheetSyncIssue> bucket = new ArrayList<>();
        Integer currentRow = null;
        for (TimesheetSyncIssue issue : rows == null ? List.<TimesheetSyncIssue>of() : rows) {
            Integer sourceRow = issue.getSourceRow();
            if (sourceRow == null) {
                flushMergedRow(merged, bucket);
                currentRow = null;
                merged.add(toIssueView(issue));
                continue;
            }
            if (currentRow != null && !currentRow.equals(sourceRow)) {
                flushMergedRow(merged, bucket);
            }
            currentRow = sourceRow;
            bucket.add(issue);
        }
        flushMergedRow(merged, bucket);
        return merged;
    }

    private static void flushMergedRow(List<IssueView> merged, List<TimesheetSyncIssue> bucket) {
        if (bucket.isEmpty()) {
            return;
        }
        merged.add(toMergedIssue(bucket));
        bucket.clear();
    }

    private static IssueView toMergedIssue(List<TimesheetSyncIssue> bucket) {
        TimesheetSyncIssue first = bucket.get(0);
        if (bucket.size() == 1) {
            return toIssueView(first);
        }
        Set<String> codes = new LinkedHashSet<>();
        for (TimesheetSyncIssue issue : bucket) {
            if (issue.getCode() != null && !issue.getCode().isBlank()) {
                codes.add(issue.getCode());
            }
        }
        return new IssueView(
                first.getId(),
                codes.isEmpty() ? first.getCode() : String.join(", ", codes),
                mergeMessages(bucket),
                firstNonBlank(bucket, TimesheetSyncIssue::getEmpCcgid),
                firstNonBlank(bucket, TimesheetSyncIssue::getPositionId),
                firstNonBlank(bucket, TimesheetSyncIssue::getPl3Code),
                first.getSourceRow());
    }

    private static IssueView toIssueView(TimesheetSyncIssue issue) {
        return new IssueView(
                issue.getId(),
                issue.getCode(),
                displayIssueMessage(issue),
                issue.getEmpCcgid(),
                issue.getPositionId(),
                issue.getPl3Code(),
                issue.getSourceRow());
    }

    private static String mergeMessages(List<TimesheetSyncIssue> bucket) {
        List<String> missing = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (TimesheetSyncIssue issue : bucket) {
            String text = displayIssueMessage(issue);
            if (text.isBlank()) {
                continue;
            }
            Matcher matcher = MISSING_FIELD.matcher(text);
            if (matcher.matches()) {
                String field = matcher.group(1).trim();
                if (!field.isEmpty() && !missing.contains(field)) {
                    missing.add(field);
                }
                continue;
            }
            if (!others.contains(text)) {
                others.add(text);
            }
        }
        List<String> parts = new ArrayList<>();
        if (!missing.isEmpty()) {
            parts.add("Missing " + String.join(", ", missing) + ".");
        }
        parts.addAll(others);
        return String.join(" ", parts);
    }

    private static String displayIssueMessage(TimesheetSyncIssue issue) {
        String text = issue.getMessage() == null ? "" : issue.getMessage().trim();
        if (issue.getSourceRow() != null) {
            text = text.replaceFirst("(?i)^Row\\s+" + issue.getSourceRow() + "\\s+", "");
            text = text.replaceFirst("(?i)^is missing ", "Missing ");
            text = text.replaceFirst("(?i)^date ", "Date ");
        }
        return text;
    }

    private static String firstNonBlank(
            List<TimesheetSyncIssue> bucket, java.util.function.Function<TimesheetSyncIssue, String> getter) {
        for (TimesheetSyncIssue issue : bucket) {
            String value = getter.apply(issue);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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

    public record Overview(List<RunHeader> daily, List<RunHeader> monthly, PageResponse<RunHeader> runs) {
    }

    public record AlertConfig(boolean enabled, List<String> recipients) {
    }

    public record RunDetail(RunHeader run, PageResponse<IssueView> issues) {
    }

    public record FileDownload(String fileName, byte[] content) {
    }

    public record RunHeader(
            UUID id,
            String kind,
            String status,
            LocalDate syncDate,
            String center,
            short attemptNo,
            Integer rowCount,
            String sourceType,
            String sourceFileName,
            String sourceEtag,
            boolean hasSourceFile,
            AuditActorView createdBy,
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
