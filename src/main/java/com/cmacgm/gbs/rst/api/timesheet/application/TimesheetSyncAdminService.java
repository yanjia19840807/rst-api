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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
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
import com.cmacgm.gbs.rst.api.security.SourceDataAccess;
import com.cmacgm.gbs.rst.api.sourcedata.application.CenterSourceService;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterSource;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceAlert;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceIssue;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceSnapshot;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SourceAlertRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SourceIssueRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SourceSnapshotRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SourceSnapshotSpecification;

/**
 * LTH monitor and manual upload.
 */
@Service
public class TimesheetSyncAdminService {

    private static final Logger log = LoggerFactory.getLogger(TimesheetSyncAdminService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern MISSING_FIELD = Pattern.compile("(?i)^Missing (.+)\\.$");
    private static final int MAX_RECIPIENTS = 20;
    private static final List<String> INTERIM_KINDS = List.of("MYHR", "SHARED_KPI");

    private final SourceSnapshotRepository syncRuns;
    private final SourceIssueRepository issues;
    private final SourceAlertRepository alerts;
    private final TimesheetSyncService syncService;
    private final AuditRecorder audits;
    private final MicrosoftGraphService graph;
    private final Clock clock;
    private final CenterSourceService centerSources;
    private final AtomicBoolean sharePointSyncRunning = new AtomicBoolean(false);

    /**
     * @param syncRuns run headers
     * @param issues issue rows
     * @param alerts alert config
     * @param syncService pipeline
     * @param audits who triggered each run
     * @param graph source file download
     * @param clock timestamps
     * @param centerSources which centers still use Timesheet
     */
    public TimesheetSyncAdminService(
            SourceSnapshotRepository syncRuns,
            SourceIssueRepository issues,
            SourceAlertRepository alerts,
            TimesheetSyncService syncService,
            AuditRecorder audits,
            MicrosoftGraphService graph,
            Clock clock,
            CenterSourceService centerSources) {
        this.syncRuns = syncRuns;
        this.issues = issues;
        this.alerts = alerts;
        this.syncService = syncService;
        this.audits = audits;
        this.graph = graph;
        this.clock = clock;
        this.centerSources = centerSources;
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
     * @param principal caller; an LTH is limited to the identity Center and Daily or Monthly
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
            int pageSize,
            RstPrincipal principal) {
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int safePage = Math.max(1, page);
        String onlyCenter = null;
        if (SourceDataAccess.centerScoped(principal)) {
            if (isInterimKind(kind) || isGlobalKind(kind)) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "source-global-forbidden",
                        "MyHR and SKPI files cover more than one Center.");
            }
            onlyCenter = SourceDataAccess.ownCenter(principal);
            center = onlyCenter;
        }
        boolean interim = isInterimKind(kind);
        Specification<SourceSnapshot> spec = SourceSnapshotSpecification.filtered(
                interim ? null : kind, status, center, sourceType, dateFrom, dateTo);
        if (interim) {
            spec = spec.and((root, query, builder) -> root.get("kind").in(INTERIM_KINDS));
        } else if (limitsToTimesheetCenters(kind)) {
            spec = spec.and(timesheetCenters());
            if (kind == null || kind.isBlank()) {
                spec = spec.and((root, query, builder) -> root.get("kind").in("DAILY", "MONTHLY"));
            }
        }
        Page<SourceSnapshot> runs = syncRuns.findAll(
                spec, PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "startedAt")));
        return new Overview(snapshots("DAILY", onlyCenter), snapshots("MONTHLY", onlyCenter), headers(runs));
    }

    /**
     * Streams the source file stored on SharePoint for this run.
     *
     * @param id run
     * @return file name and bytes
     */
    @Transactional(readOnly = true)
    public FileDownload downloadSource(UUID id, RstPrincipal principal) {
        SourceSnapshot run = syncRuns.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Timesheet sync run not found."));
        SourceDataAccess.requireOwnSnapshot(principal, run.getKind(), run.getCenter());
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
    public RunDetail run(UUID id, int page, int pageSize, RstPrincipal principal) {
        SourceSnapshot run = syncRuns.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Timesheet sync run not found."));
        SourceDataAccess.requireOwnSnapshot(principal, run.getKind(), run.getCenter());
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int safePage = Math.max(1, page);
        List<IssueView> merged = mergeBySourceRow(issues.findBySyncRunIdOrderBySourceRowAscCreatedAtAsc(id));
        PageResponse<IssueView> issuePage = PageResponse.ofList(merged, safePage, safePageSize);
        if (issuePage.total() == 0 && run.getErrorCode() != null) {
            issuePage = PageResponse.ofList(List.of(runLevelIssue(run)), 1, safePageSize);
        }
        return new RunDetail(toHeader(run), issuePage);
    }

    private IssueView runLevelIssue(SourceSnapshot run) {
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
        SourceAlert row = alerts.findById(SourceAlert.SINGLETON_ID)
                .orElseGet(SourceAlert::disabled);
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
        SourceAlert row = alerts.findById(SourceAlert.SINGLETON_ID)
                .orElseGet(SourceAlert::disabled);
        row.replace(
                incoming.enabled(),
                String.join("\n", recipients),
                clock.instant(),
                principal == null ? "SYSTEM" : principal.ccgid());
        alerts.save(row);
        return new AlertConfig(row.isEnabled(), recipients);
    }

    /**
     * Starts a SharePoint Daily then Monthly sync for every center. Returns while the
     * work continues. The signed-in user stays on the worker thread so each run is
     * audited as that person, not {@code SYSTEM}.
     *
     * @param principal global admin
     * @return running status
     */
    public SharePointSyncStatus startSharePointSync(RstPrincipal principal) {
        SourceDataAccess.requireGlobal(principal);
        if (!sharePointSyncRunning.compareAndSet(false, true)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.SYNC_IN_PROGRESS.code(),
                    "A SharePoint Timesheet sync is already running.");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = principal == null ? "SYSTEM" : principal.ccgid();
        try {
            Thread.ofVirtual().name("timesheet-sharepoint-sync").start(() -> runSharePointSync(authentication, actor));
        } catch (RuntimeException ex) {
            sharePointSyncRunning.set(false);
            throw ex;
        }
        return new SharePointSyncStatus(true);
    }

    /**
     * @param principal global admin
     * @return whether a manual SharePoint sync is still in progress
     */
    public SharePointSyncStatus sharePointSyncStatus(RstPrincipal principal) {
        SourceDataAccess.requireGlobal(principal);
        return new SharePointSyncStatus(sharePointSyncRunning.get());
    }

    private void runSharePointSync(Authentication authentication, String actor) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        log.info("Manual SharePoint Timesheet sync starting: actor={}", actor);
        try {
            runSharePointKind("DAILY", actor);
            runSharePointKind("MONTHLY", actor);
            log.info("Manual SharePoint Timesheet sync finished: actor={}", actor);
        } finally {
            SecurityContextHolder.clearContext();
            sharePointSyncRunning.set(false);
        }
    }

    private void runSharePointKind(String kind, String actor) {
        try {
            syncService.syncFromSharePoint(kind);
        } catch (RuntimeException ex) {
            log.error(
                    "Manual SharePoint Timesheet sync failed: kind={} actor={} {}",
                    kind,
                    actor,
                    ex.getMessage(),
                    ex);
        }
    }

    /**
     * Stores the file on SharePoint Manual/Timesheet and syncs immediately.
     * The signed-in user is recorded on the sync audit event.
     *
     * @param file upload
     * @return result header
     */
    public RunHeader upload(MultipartFile file, RstPrincipal principal) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, TimesheetSyncErrorCode.INVALID_HEADER.code(), "Timesheet file is required.");
        }
        String name = file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename();
        if (SourceDataAccess.centerScoped(principal)) {
            String own = SourceDataAccess.ownCenter(principal);
            String fileCenter = TimesheetReportName.parse(name)
                    .map(TimesheetReportName.Parsed::region)
                    .orElse(null);
            if (!own.equals(fileCenter)) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "source-center-forbidden",
                        "Source Data is limited to Center " + own + ".");
            }
            if (!CenterSource.TIMESHEET.equals(centerSources.sourceMode(own))) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "source-global-forbidden",
                        "This Center uses MyHR and SKPI.");
            }
        }
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

    private List<RunHeader> snapshots(String kind, String onlyCenter) {
        Set<String> allowed = timesheetCenterNames();
        List<SourceSnapshot> rows = syncRuns.findByKindAndStatus(kind, "ACTIVE").stream()
                .filter(run -> allowed.contains(run.getCenter()))
                .filter(run -> onlyCenter == null || onlyCenter.equals(run.getCenter()))
                .toList();
        Map<UUID, AuditActorView> created = createdBy(rows);
        return rows.stream().map(run -> toHeader(run, created.get(run.getId()))).toList();
    }

    /**
     * Kind token for the MyHR and SKPI list when no single kind is selected.
     */
    private static boolean isInterimKind(String kind) {
        return kind != null && "INTERIM".equalsIgnoreCase(kind.trim());
    }

    private static boolean isGlobalKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return false;
        }
        String normalized = kind.trim().toUpperCase(Locale.ROOT);
        return "MYHR".equals(normalized) || "SHARED_KPI".equals(normalized);
    }

    /**
     * Daily and Monthly history belongs to centers that still use Timesheet.
     * MyHR and SKPI uploads are global and stay on their own list.
     */
    private static boolean limitsToTimesheetCenters(String kind) {
        if (kind == null || kind.isBlank()) {
            return true;
        }
        String normalized = kind.trim().toUpperCase(Locale.ROOT);
        return "DAILY".equals(normalized) || "MONTHLY".equals(normalized);
    }

    private Specification<SourceSnapshot> timesheetCenters() {
        Set<String> allowed = timesheetCenterNames();
        if (allowed.isEmpty()) {
            return (root, query, builder) -> builder.disjunction();
        }
        return (root, query, builder) -> root.get("center").in(allowed);
    }

    private Set<String> timesheetCenterNames() {
        return centerSources.centersWith(CenterSource.TIMESHEET);
    }

    private PageResponse<RunHeader> headers(Page<SourceSnapshot> page) {
        Map<UUID, AuditActorView> created = createdBy(page.getContent());
        return PageResponse.from(page, run -> toHeader(run, created.get(run.getId())));
    }

    private Map<UUID, AuditActorView> createdBy(List<SourceSnapshot> rows) {
        return audits.createdBy(
                AuditEntityType.TIMESHEET_SYNC,
                rows.stream().map(SourceSnapshot::getId).toList());
    }

    private RunHeader toHeader(SourceSnapshot run) {
        return toHeader(run, audits.createdBy(AuditEntityType.TIMESHEET_SYNC, run.getId()));
    }

    private RunHeader toHeader(SourceSnapshot run, AuditActorView createdBy) {
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
    static List<IssueView> mergeBySourceRow(List<SourceIssue> rows) {
        List<IssueView> merged = new ArrayList<>();
        List<SourceIssue> bucket = new ArrayList<>();
        Integer currentRow = null;
        for (SourceIssue issue : rows == null ? List.<SourceIssue>of() : rows) {
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

    private static void flushMergedRow(List<IssueView> merged, List<SourceIssue> bucket) {
        if (bucket.isEmpty()) {
            return;
        }
        merged.add(toMergedIssue(bucket));
        bucket.clear();
    }

    private static IssueView toMergedIssue(List<SourceIssue> bucket) {
        SourceIssue first = bucket.get(0);
        if (bucket.size() == 1) {
            return toIssueView(first);
        }
        Set<String> codes = new LinkedHashSet<>();
        for (SourceIssue issue : bucket) {
            if (issue.getCode() != null && !issue.getCode().isBlank()) {
                codes.add(issue.getCode());
            }
        }
        return new IssueView(
                first.getId(),
                codes.isEmpty() ? first.getCode() : String.join(", ", codes),
                mergeMessages(bucket),
                firstNonBlank(bucket, SourceIssue::getEmpCcgid),
                firstNonBlank(bucket, SourceIssue::getPositionId),
                firstNonBlank(bucket, SourceIssue::getPl3Code),
                first.getSourceRow());
    }

    private static IssueView toIssueView(SourceIssue issue) {
        return new IssueView(
                issue.getId(),
                issue.getCode(),
                displayIssueMessage(issue),
                issue.getEmpCcgid(),
                issue.getPositionId(),
                issue.getPl3Code(),
                issue.getSourceRow());
    }

    private static String mergeMessages(List<SourceIssue> bucket) {
        List<String> missing = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (SourceIssue issue : bucket) {
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

    private static String displayIssueMessage(SourceIssue issue) {
        String text = issue.getMessage() == null ? "" : issue.getMessage().trim();
        if (issue.getSourceRow() != null) {
            text = text.replaceFirst("(?i)^Row\\s+" + issue.getSourceRow() + "\\s+", "");
            text = text.replaceFirst("(?i)^is missing ", "Missing ");
            text = text.replaceFirst("(?i)^date ", "Date ");
        }
        return text;
    }

    private static String firstNonBlank(
            List<SourceIssue> bucket, java.util.function.Function<SourceIssue, String> getter) {
        for (SourceIssue issue : bucket) {
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

    /**
     * @param running true while a manual SharePoint sync is in progress
     */
    public record SharePointSyncStatus(boolean running) {
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
