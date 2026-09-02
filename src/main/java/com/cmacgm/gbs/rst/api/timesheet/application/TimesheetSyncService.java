package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.ReportRow;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSourceResolver.Source;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetAssignmentRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetKpiRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetScopeRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncIssueRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;

/**
 * One pipeline for Daily and Monthly Timesheet sync.
 */
@Service
public class TimesheetSyncService {

    private static final Logger log = LoggerFactory.getLogger(TimesheetSyncService.class);

    private final TimesheetReportParser parser;
    private final TimesheetDailyCalculator dailyCalculator;
    private final TimesheetMonthlyCalculator monthlyCalculator;
    private final TimesheetSourceResolver sources;
    private final GbsProcessCatalogSource processCatalogs;
    private final TimesheetSyncRunRepository syncRuns;
    private final TimesheetPersonRepository people;
    private final TimesheetPositionRepository positions;
    private final TimesheetScopeRepository scopes;
    private final TimesheetAssignmentRepository assignments;
    private final TimesheetKpiRepository kpis;
    private final TimesheetSyncIssueRepository issues;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final TimesheetSyncAlertNotifier alertNotifier;

    /**
     * @param parser report parser
     * @param dailyCalculator Daily compute
     * @param monthlyCalculator Monthly compute
     * @param sources SharePoint source
     * @param processCatalogs GBS Process catalog
     * @param syncRuns run repository
     * @param people person repository
     * @param positions position repository
     * @param scopes scope repository
     * @param assignments assignment repository
     * @param kpis KPI repository
     * @param issues issue repository
     * @param transactionManager cutover transactions
     * @param clock timestamps
     * @param alertNotifier failure email
     */
    public TimesheetSyncService(
            TimesheetReportParser parser,
            TimesheetDailyCalculator dailyCalculator,
            TimesheetMonthlyCalculator monthlyCalculator,
            TimesheetSourceResolver sources,
            GbsProcessCatalogSource processCatalogs,
            TimesheetSyncRunRepository syncRuns,
            TimesheetPersonRepository people,
            TimesheetPositionRepository positions,
            TimesheetScopeRepository scopes,
            TimesheetAssignmentRepository assignments,
            TimesheetKpiRepository kpis,
            TimesheetSyncIssueRepository issues,
            PlatformTransactionManager transactionManager,
            Clock clock,
            TimesheetSyncAlertNotifier alertNotifier) {
        this.parser = parser;
        this.dailyCalculator = dailyCalculator;
        this.monthlyCalculator = monthlyCalculator;
        this.sources = sources;
        this.processCatalogs = processCatalogs;
        this.syncRuns = syncRuns;
        this.people = people;
        this.positions = positions;
        this.scopes = scopes;
        this.assignments = assignments;
        this.kpis = kpis;
        this.issues = issues;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.alertNotifier = alertNotifier;
    }

    /**
     * Syncs both kinds.
     *
     * @return daily then monthly results
     */
    public List<SyncResult> syncAll() {
        return List.of(sync("DAILY"), sync("MONTHLY"));
    }

    /**
     * Syncs one kind from the configured source.
     *
     * @param kind DAILY or MONTHLY
     * @return result
     */
    public SyncResult sync(String kind) {
        return syncFromSharePoint(kind, "SYSTEM");
    }

    /**
     * Uploads to Manual then parses. The file is stored even when parse fails.
     *
     * @param fileName original name
     * @param content file bytes
     * @param actorCcgid LTH
     * @return result
     */
    public SyncResult syncUploaded(String fileName, byte[] content, String actorCcgid) {
        Source source = sources.storeManual(fileName, content == null ? new byte[0] : content);
        String kind = TimesheetReportName.parse(source.fileName())
                .map(TimesheetReportName.Parsed::kind)
                .orElseGet(() -> guessKind(source.fileName()));
        return syncFromSource(kind, source, actorCcgid == null ? "SYSTEM" : actorCcgid);
    }

    /**
     * Syncs one kind from SharePoint.
     *
     * @param kind DAILY or MONTHLY
     * @param actorCcgid SYSTEM or a person
     * @return result
     */
    public SyncResult syncFromSharePoint(String kind, String actorCcgid) {
        String normalized = normalizeKind(kind);
        String actor = actorCcgid == null ? "SYSTEM" : actorCcgid;
        Source source;
        try {
            source = sources.open(normalized);
        } catch (ApiException ex) {
            failWithoutRows(
                    normalized,
                    new Source(null, InputStream.nullInputStream(), null, null, "SHAREPOINT", null),
                    actor,
                    ex.code(),
                    ex.getMessage());
            throw ex;
        }
        return syncFromSource(normalized, source, actor);
    }

    private SyncResult syncFromSource(String kind, Source source, String actorCcgid) {
        if (syncRuns.findByKindAndStatus(kind, "LOADING").isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.SYNC_IN_PROGRESS.code(),
                    "A " + kind + " Timesheet sync is already running.");
        }
        var active = syncRuns.findByKindAndStatus(kind, "ACTIVE");
        if (source.filenameDate() != null && active.isPresent()
                && source.filenameDate().isBefore(active.get().getSyncDate())) {
            log.info("Timesheet {} stale file skipped: {}", kind, source.fileName());
            return toResult(active.get());
        }
        if (!isManual(source)
                && source.filenameDate() != null
                && active.isPresent()
                && source.filenameDate().equals(active.get().getSyncDate())
                && source.driveItemId() != null
                && source.etag() != null) {
            var same = syncRuns.findByKindAndStatusAndSourceDriveItemIdAndSourceEtag(
                    kind, "ACTIVE", source.driveItemId(), source.etag());
            if (same.isPresent()) {
                log.info("Timesheet {} unchanged: id={}", kind, same.get().getId());
                return toResult(same.get());
            }
        }
        List<ReportRow> rows;
        try (InputStream in = source.content()) {
            rows = parser.parse(in, source.fileName());
        } catch (ApiException ex) {
            failWithoutRows(kind, source, actorCcgid, ex.code(), ex.getMessage());
            throw ex;
        } catch (IOException ex) {
            ApiException wrapped = new ApiException(
                    HttpStatus.BAD_REQUEST,
                    TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                    "Unable to read Timesheet file: " + ex.getMessage());
            failWithoutRows(kind, source, actorCcgid, wrapped.code(), wrapped.getMessage());
            throw wrapped;
        }
        return persist(kind, source, rows, actorCcgid);
    }

    private SyncResult persist(String kind, Source source, List<ReportRow> rows, String actorCcgid) {
        Instant now = clock.instant();
        String hash = hashRows(rows);
        var unchanged = syncRuns.findByKindAndStatus(kind, "ACTIVE")
                .filter(active -> hash.equals(active.getDataHash()));
        if (unchanged.isPresent() && !isManual(source)) {
            log.info("Timesheet {} hash unchanged: id={}", kind, unchanged.get().getId());
            return toResult(unchanged.get());
        }
        LocalDate previewDate = source.filenameDate() != null ? source.filenameDate() : previewDate(kind, rows);
        TimesheetSyncRun run = TimesheetSyncRun.startLoading(
                kind, previewDate, nextAttemptNo(kind, previewDate), now);
        run.setSource(
                source.driveItemId(),
                source.etag(),
                source.sourceType(),
                source.fileName(),
                actorCcgid);
        run.setCenter(resolveCenter(source));
        syncRuns.saveAndFlush(run);
        try {
            GbsProcessCatalog catalog = processCatalogs.load();
            if ("DAILY".equals(kind)) {
                TimesheetDailyCalculator.Result computed =
                        dailyCalculator.compute(run.getId(), rows, now, source.filenameDate(), catalog);
                return activateOrFail(run, rows.size(), hash, computed.issues(), () -> {
                    people.saveAll(computed.people());
                    positions.saveAll(computed.positions());
                });
            }
            TimesheetMonthlyCalculator.Result computed =
                    monthlyCalculator.compute(run.getId(), rows, now, source.filenameDate(), catalog);
            return activateOrFail(run, rows.size(), hash, computed.issues(), () -> {
                scopes.saveAll(computed.scopes());
                assignments.saveAll(computed.assignments());
                kpis.saveAll(computed.kpis());
            });
        } catch (RuntimeException ex) {
            String code = errorCodeOf(ex);
            String message = userMessage(ex);
            markFailed(run.getId(), code, message);
            persistRunIssueIfMissing(run.getId(), code, message, now);
            notifyFailed(run.getId());
            throw ex instanceof ApiException ? ex : new ApiException(HttpStatus.CONFLICT, code, message);
        }
    }

    private LocalDate previewDate(String kind, List<ReportRow> rows) {
        if ("DAILY".equals(kind)) {
            return rows.stream()
                    .map(ReportRow::date)
                    .filter(date -> date != null)
                    .findFirst()
                    .orElseGet(() -> LocalDate.now(clock));
        }
        return rows.stream()
                .map(row -> row.date() != null
                        ? row.date()
                        : row.month() == null ? null : row.month().withDayOfMonth(row.month().lengthOfMonth()))
                .filter(date -> date != null)
                .findFirst()
                .orElseGet(() -> LocalDate.now(clock));
    }

    private SyncResult activateOrFail(
            TimesheetSyncRun run,
            int rowCount,
            String dataHash,
            List<TimesheetSyncIssue> computedIssues,
            Runnable persistRows) {
        if (!computedIssues.isEmpty()) {
            issues.saveAll(computedIssues);
        }
        List<TimesheetSyncIssue> blocking = computedIssues.stream()
                .filter(issue -> !TimesheetRowValidator.isAdvisory(issue))
                .toList();
        if (!blocking.isEmpty()) {
            markFailed(run.getId(), blocking.getFirst().getCode(), blocking.getFirst().getMessage());
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    blocking.getFirst().getCode(),
                    blocking.getFirst().getMessage());
        }
        persistRows.run();
        activate(run.getId(), run.getKind(), rowCount, dataHash);
        TimesheetSyncRun active = syncRuns.findById(run.getId()).orElseThrow();
        log.info(
                "Timesheet {} activated: id={} syncDate={} rows={}",
                active.getKind(),
                active.getId(),
                active.getSyncDate(),
                active.getRowCount());
        return new SyncResult(
                active.getId(),
                active.getKind(),
                active.getSyncDate(),
                active.getAttemptNo(),
                active.getStatus(),
                active.getRowCount(),
                active.getDataHash());
    }

    private void activate(UUID runId, String kind, int rowCount, String dataHash) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = clock.instant();
            TimesheetSyncRun run = syncRuns.findById(runId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT,
                            TimesheetSyncErrorCode.COUNT_MISMATCH.code(),
                            "Sync run disappeared before activation."));
            syncRuns.archiveOtherActive(kind, run.getId(), now);
            run.markActive(rowCount, dataHash, now);
            syncRuns.saveAndFlush(run);
            dropStaleComputed(kind, run.getId());
        });
    }

    private void dropStaleComputed(String kind, UUID keepRunId) {
        if ("DAILY".equals(kind)) {
            int peopleDropped = people.deleteBySyncRunIdNot(keepRunId);
            int positionsDropped = positions.deleteBySyncRunIdNot(keepRunId);
            log.info(
                    "Timesheet DAILY kept computed rows for {}; dropped people={} positions={}",
                    keepRunId,
                    peopleDropped,
                    positionsDropped);
            return;
        }
        int scopesDropped = scopes.deleteBySyncRunIdNot(keepRunId);
        int assignmentsDropped = assignments.deleteBySyncRunIdNot(keepRunId);
        int kpisDropped = kpis.deleteBySyncRunIdNot(keepRunId);
        log.info(
                "Timesheet MONTHLY kept computed rows for {}; dropped scopes={} assignments={} kpis={}",
                keepRunId,
                scopesDropped,
                assignmentsDropped,
                kpisDropped);
    }

    private void failWithoutRows(String kind, Source source, String actorCcgid, String code, String message) {
        Instant now = clock.instant();
        LocalDate date = source.filenameDate() != null ? source.filenameDate() : LocalDate.now(clock);
        TimesheetSyncRun run = TimesheetSyncRun.startLoading(kind, date, nextAttemptNo(kind, date), now);
        run.setSource(
                source.driveItemId(), source.etag(), source.sourceType(), source.fileName(), actorCcgid);
        run.setCenter(resolveCenter(source));
        run.markFailed(code, sanitize(message), now);
        syncRuns.save(run);
        persistRunIssueIfMissing(run.getId(), code, sanitize(message), now);
        notifyFailed(run.getId());
    }

    private void persistRunIssueIfMissing(UUID runId, String code, String message, Instant now) {
        if (!issues.findBySyncRunIdOrderBySourceRowAscCreatedAtAsc(runId).isEmpty()) {
            return;
        }
        issues.save(TimesheetSyncIssue.error(runId, code, message, null, null, null, null, null, now));
    }

    private static String normalizeKind(String kind) {
        String normalized = kind.trim().toUpperCase(Locale.ROOT);
        if (!"DAILY".equals(normalized) && !"MONTHLY".equals(normalized)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    TimesheetSyncErrorCode.INVALID_HEADER.code(),
                    "Unknown Timesheet kind: " + kind);
        }
        return normalized;
    }

    private static String guessKind(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lower.contains("monthly") ? "MONTHLY" : "DAILY";
    }

    private static String resolveCenter(Source source) {
        return TimesheetReportName.parse(source == null ? null : source.fileName())
                .map(TimesheetReportName.Parsed::region)
                .map(String::trim)
                .orElse("");
    }

    private static boolean isManual(Source source) {
        return source != null && "MANUAL".equalsIgnoreCase(source.sourceType());
    }

    private static SyncResult toResult(TimesheetSyncRun run) {
        return new SyncResult(
                run.getId(),
                run.getKind(),
                run.getSyncDate(),
                run.getAttemptNo(),
                run.getStatus(),
                run.getRowCount(),
                run.getDataHash());
    }

    private void notifyFailed(UUID runId) {
        if (alertNotifier != null) {
            alertNotifier.notifyFailed(runId);
        }
    }

    private void markFailed(UUID runId, String errorCode, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            syncRuns.findById(runId).ifPresent(run -> {
                run.markFailed(errorCode, message, clock.instant());
                syncRuns.save(run);
            });
        });
    }

    private short nextAttemptNo(String kind, LocalDate syncDate) {
        Short max = syncRuns.findMaxAttemptNo(kind, syncDate);
        int next = (max == null ? 0 : max) + 1;
        if (next > Short.MAX_VALUE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.COUNT_MISMATCH.code(),
                    "Too many sync attempts for " + kind + " " + syncDate);
        }
        return (short) next;
    }

    private static String hashRows(List<ReportRow> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ReportRow row : rows) {
                digest.update(String.valueOf(row.sourceRow()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.date()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.month()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.empCcgid()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.empPositionId()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.supervisorPositionId()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.pl3Code()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.center()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.domain()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.pl1()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.pl2()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.pl3Name()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.carrier()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.site()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.customerCountry()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(String.valueOf(row.hc() == null ? "" : row.hc().value()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String errorCodeOf(RuntimeException ex) {
        if (ex instanceof ApiException api) {
            return api.code();
        }
        if (isActiveConstraint(ex)) {
            return TimesheetSyncErrorCode.CUTOVER_CONFLICT.code();
        }
        return TimesheetSyncErrorCode.COUNT_MISMATCH.code();
    }

    private static String userMessage(RuntimeException ex) {
        if (ex instanceof ApiException api) {
            return sanitize(api.getMessage());
        }
        if (isActiveConstraint(ex)) {
            return "Could not replace the current ACTIVE Timesheet snapshot. Retry the sync.";
        }
        return "Timesheet sync failed unexpectedly.";
    }

    private static boolean isActiveConstraint(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            String text = current.getMessage() == null ? "" : current.getMessage();
            if (text.contains("uk_timesheet_one_active_run_per_kind")) {
                return true;
            }
        }
        return false;
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "Timesheet sync failed.";
        }
        if (message.contains("uk_timesheet_one_active_run_per_kind") || looksLikeSql(message)) {
            return "Timesheet sync failed unexpectedly.";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private static boolean looksLikeSql(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("batch entry")
                || lower.contains("duplicate key")
                || lower.contains("sql [")
                || lower.contains("could not execute");
    }

    /**
     * Successful or skipped sync.
     */
    public record SyncResult(
            UUID id,
            String kind,
            LocalDate syncDate,
            short attemptNo,
            String status,
            Integer rowCount,
            String dataHash) {
    }
}
