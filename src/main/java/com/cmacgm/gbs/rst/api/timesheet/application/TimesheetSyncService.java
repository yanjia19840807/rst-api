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
    private final TimesheetSyncRunRepository syncRuns;
    private final TimesheetPersonRepository people;
    private final TimesheetPositionRepository positions;
    private final TimesheetScopeRepository scopes;
    private final TimesheetAssignmentRepository assignments;
    private final TimesheetKpiRepository kpis;
    private final TimesheetSyncIssueRepository issues;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * @param parser report parser
     * @param dailyCalculator Daily compute
     * @param monthlyCalculator Monthly compute
     * @param sources SharePoint source
     * @param syncRuns run repository
     * @param people person repository
     * @param positions position repository
     * @param scopes scope repository
     * @param assignments assignment repository
     * @param kpis KPI repository
     * @param issues issue repository
     * @param transactionManager cutover transactions
     * @param clock timestamps
     */
    public TimesheetSyncService(
            TimesheetReportParser parser,
            TimesheetDailyCalculator dailyCalculator,
            TimesheetMonthlyCalculator monthlyCalculator,
            TimesheetSourceResolver sources,
            TimesheetSyncRunRepository syncRuns,
            TimesheetPersonRepository people,
            TimesheetPositionRepository positions,
            TimesheetScopeRepository scopes,
            TimesheetAssignmentRepository assignments,
            TimesheetKpiRepository kpis,
            TimesheetSyncIssueRepository issues,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.parser = parser;
        this.dailyCalculator = dailyCalculator;
        this.monthlyCalculator = monthlyCalculator;
        this.sources = sources;
        this.syncRuns = syncRuns;
        this.people = people;
        this.positions = positions;
        this.scopes = scopes;
        this.assignments = assignments;
        this.kpis = kpis;
        this.issues = issues;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
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
        String normalized = kind.trim().toUpperCase(Locale.ROOT);
        if (!"DAILY".equals(normalized) && !"MONTHLY".equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HEADER", "Unknown Timesheet kind: " + kind);
        }
        Source source = sources.open(normalized);
        if (source.driveItemId() != null && source.etag() != null) {
            var existing = syncRuns.findByKindAndStatusAndSourceDriveItemIdAndSourceEtag(
                    normalized, "ACTIVE", source.driveItemId(), source.etag());
            if (existing.isPresent()) {
                TimesheetSyncRun active = existing.get();
                log.info("Timesheet {} unchanged: id={}", normalized, active.getId());
                return new SyncResult(
                        active.getId(),
                        normalized,
                        active.getSyncDate(),
                        active.getAttemptNo(),
                        active.getStatus(),
                        active.getRowCount(),
                        active.getDataHash());
            }
        }
        try (InputStream in = source.content()) {
            List<ReportRow> rows = parser.parse(in, source.fileName());
            return persist(normalized, source, rows);
        } catch (ApiException ex) {
            failWithoutRows(normalized, source, ex.code(), ex.getMessage());
            throw ex;
        } catch (IOException ex) {
            ApiException wrapped = new ApiException(
                    HttpStatus.BAD_REQUEST, "SOURCE_UNAVAILABLE", "Unable to read Timesheet file: " + ex.getMessage());
            failWithoutRows(normalized, source, wrapped.code(), wrapped.getMessage());
            throw wrapped;
        }
    }

    private SyncResult persist(String kind, Source source, List<ReportRow> rows) {
        Instant now = clock.instant();
        String hash = hashRows(rows);
        var unchanged = syncRuns.findByKindAndStatus(kind, "ACTIVE")
                .filter(active -> hash.equals(active.getDataHash()));
        if (unchanged.isPresent()) {
            TimesheetSyncRun active = unchanged.get();
            log.info("Timesheet {} hash unchanged: id={}", kind, active.getId());
            return new SyncResult(
                    active.getId(),
                    kind,
                    active.getSyncDate(),
                    active.getAttemptNo(),
                    active.getStatus(),
                    active.getRowCount(),
                    active.getDataHash());
        }
        LocalDate previewDate = previewDate(kind, rows);
        TimesheetSyncRun run = TimesheetSyncRun.startLoading(
                kind, previewDate, nextAttemptNo(kind, previewDate), now);
        run.setSource(source.driveItemId(), source.etag());
        syncRuns.saveAndFlush(run);
        try {
            if ("DAILY".equals(kind)) {
                TimesheetDailyCalculator.Result computed = dailyCalculator.compute(run.getId(), rows, now);
                return activateOrFail(run, rows.size(), hash, computed.issues(), () -> {
                    people.saveAll(computed.people());
                    positions.saveAll(computed.positions());
                });
            }
            TimesheetMonthlyCalculator.Result computed = monthlyCalculator.compute(run.getId(), rows, now);
            return activateOrFail(run, rows.size(), hash, computed.issues(), () -> {
                scopes.saveAll(computed.scopes());
                assignments.saveAll(computed.assignments());
                kpis.saveAll(computed.kpis());
            });
        } catch (RuntimeException ex) {
            markFailed(run.getId(), errorCodeOf(ex), sanitize(ex.getMessage()));
            throw ex;
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
            markFailed(run.getId(), computedIssues.getFirst().getCode(), computedIssues.getFirst().getMessage());
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    computedIssues.getFirst().getCode(),
                    computedIssues.getFirst().getMessage());
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
                            HttpStatus.CONFLICT, "COUNT_MISMATCH", "Sync run disappeared before activation."));
            syncRuns.findByKindAndStatus(kind, "ACTIVE").ifPresent(previous -> {
                if (!previous.getId().equals(run.getId())) {
                    previous.markArchived(now);
                    syncRuns.saveAndFlush(previous);
                }
            });
            run.markActive(rowCount, dataHash, now);
            syncRuns.save(run);
        });
    }

    private void failWithoutRows(String kind, Source source, String code, String message) {
        Instant now = clock.instant();
        LocalDate date = LocalDate.now(clock);
        TimesheetSyncRun run = TimesheetSyncRun.startLoading(kind, date, nextAttemptNo(kind, date), now);
        run.setSource(source.driveItemId(), source.etag());
        run.markFailed(code, sanitize(message), now);
        syncRuns.save(run);
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
                    HttpStatus.CONFLICT, "COUNT_MISMATCH", "Too many sync attempts for " + kind + " " + syncDate);
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
        return "COUNT_MISMATCH";
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "Timesheet sync failed.";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
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
