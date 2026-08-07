package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSnapshotRow;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSnapshotRowRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;

/**
 * Executes a full Timesheet snapshot sync from Monthly Report Excel into PostgreSQL.
 */
@Service
public class TimesheetSyncService {

    private static final Logger log = LoggerFactory.getLogger(TimesheetSyncService.class);
    private static final int BATCH_SIZE = 500;

    private final TimesheetExcelParser parser;
    private final TimesheetSyncRunRepository syncRuns;
    private final TimesheetSnapshotRowRepository snapshotRows;
    private final ResourceLoader resourceLoader;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * @param parser Excel parser
     * @param syncRuns sync run repository
     * @param snapshotRows snapshot row repository
     * @param resourceLoader Spring resource loader
     * @param transactionManager transaction manager for cutover
     * @param clock clock for sync timestamps
     */
    public TimesheetSyncService(
            TimesheetExcelParser parser,
            TimesheetSyncRunRepository syncRuns,
            TimesheetSnapshotRowRepository snapshotRows,
            ResourceLoader resourceLoader,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.parser = parser;
        this.syncRuns = syncRuns;
        this.snapshotRows = snapshotRows;
        this.resourceLoader = resourceLoader;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Loads an Excel resource as a full snapshot and activates it when validation passes.
     *
     * @param fileLocation Spring resource location (classpath: or file:)
     * @param sheetName preferred sheet name
     * @param syncDate business sync date written on the run header
     * @return activated sync summary
     */
    public SyncResult sync(String fileLocation, String sheetName, LocalDate syncDate) {
        Resource resource = resourceLoader.getResource(fileLocation);
        if (!resource.exists()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_HEADER",
                    "Timesheet file not found: " + fileLocation);
        }

        List<TimesheetExcelParser.DraftRow> drafts;
        try (InputStream in = resource.getInputStream()) {
            drafts = parser.parse(in, sheetName);
        } catch (IOException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_HEADER",
                    "Unable to open Timesheet file: " + ex.getMessage());
        }

        String dataHash = hashDrafts(drafts);
        Instant startedAt = clock.instant();
        short attemptNo = nextAttemptNo(syncDate);

        TimesheetSyncRun run = TimesheetSyncRun.startLoading(syncDate, attemptNo, startedAt);
        syncRuns.saveAndFlush(run);

        try {
            persistRows(run, drafts);
            activate(run.getId(), drafts.size(), dataHash);
            TimesheetSyncRun active = syncRuns.findById(run.getId()).orElseThrow();
            log.info(
                    "Timesheet sync activated: id={}, syncDate={}, attempt={}, rows={}",
                    active.getId(),
                    active.getSyncDate(),
                    active.getAttemptNo(),
                    active.getRowCount());
            return new SyncResult(
                    active.getId(),
                    active.getSyncDate(),
                    active.getAttemptNo(),
                    active.getStatus(),
                    active.getRowCount(),
                    active.getDataHash());
        } catch (RuntimeException ex) {
            markFailed(run.getId(), errorCodeOf(ex), sanitize(ex.getMessage()));
            throw ex;
        }
    }

    private void persistRows(TimesheetSyncRun run, List<TimesheetExcelParser.DraftRow> drafts) {
        List<TimesheetSnapshotRow> batch = new ArrayList<>(BATCH_SIZE);
        for (TimesheetExcelParser.DraftRow draft : drafts) {
            batch.add(TimesheetSnapshotRow.create(
                    run,
                    draft.empCcgid(),
                    draft.empName(),
                    draft.empPositionId(),
                    draft.supervisorCcgid(),
                    draft.supervisorName(),
                    draft.supervisorPositionId(),
                    draft.srManagerCcgid(),
                    draft.srManagerName(),
                    draft.srManagerPositionId(),
                    draft.domainHeadCcgid(),
                    draft.domainHeadName(),
                    draft.domainHeadPositionId(),
                    draft.center(),
                    draft.site(),
                    draft.domain(),
                    draft.pl1(),
                    draft.pl2(),
                    draft.pl3Code(),
                    draft.pl3Name(),
                    draft.carrier(),
                    draft.customerCountry(),
                    draft.hc()));
            if (batch.size() >= BATCH_SIZE) {
                snapshotRows.saveAll(batch);
                snapshotRows.flush();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            snapshotRows.saveAll(batch);
            snapshotRows.flush();
        }
    }

    private void activate(UUID runId, int rowCount, String dataHash) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = clock.instant();
            TimesheetSyncRun run = syncRuns.findById(runId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT,
                            "COUNT_MISMATCH",
                            "Sync run disappeared before activation."));
            syncRuns.findByStatus("ACTIVE").ifPresent(previous -> {
                if (!previous.getId().equals(run.getId())) {
                    previous.markArchived(now);
                    syncRuns.save(previous);
                }
            });
            run.markActive(rowCount, dataHash, now);
            syncRuns.save(run);
        });
    }

    private void markFailed(UUID runId, String errorCode, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            syncRuns.findById(runId).ifPresent(run -> {
                run.markFailed(errorCode, message, clock.instant());
                syncRuns.save(run);
            });
        });
    }

    private short nextAttemptNo(LocalDate syncDate) {
        Short max = syncRuns.findMaxAttemptNo(syncDate);
        int next = (max == null ? 0 : max) + 1;
        if (next > Short.MAX_VALUE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "COUNT_MISMATCH",
                    "Too many sync attempts for date " + syncDate);
        }
        return (short) next;
    }

    private static String hashDrafts(List<TimesheetExcelParser.DraftRow> drafts) {
        List<TimesheetExcelParser.DraftRow> ordered = new ArrayList<>(drafts);
        ordered.sort(Comparator
                .comparing(TimesheetExcelParser.DraftRow::empCcgid)
                .thenComparing(TimesheetExcelParser.DraftRow::pl3Code)
                .thenComparing(r -> Optional.ofNullable(r.carrier()).orElse(""))
                .thenComparing(r -> Optional.ofNullable(r.customerCountry()).orElse(""))
                .thenComparing(TimesheetExcelParser.DraftRow::site)
                .thenComparing(r -> r.hc().toPlainString()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (TimesheetExcelParser.DraftRow row : ordered) {
                String line = String.join("|",
                        nullToEmpty(row.empCcgid()),
                        nullToEmpty(row.empPositionId()),
                        nullToEmpty(row.supervisorCcgid()),
                        nullToEmpty(row.supervisorPositionId()),
                        nullToEmpty(row.srManagerCcgid()),
                        nullToEmpty(row.domainHeadCcgid()),
                        nullToEmpty(row.center()),
                        nullToEmpty(row.site()),
                        nullToEmpty(row.domain()),
                        nullToEmpty(row.pl1()),
                        nullToEmpty(row.pl2()),
                        nullToEmpty(row.pl3Code()),
                        nullToEmpty(row.carrier()),
                        nullToEmpty(row.customerCountry()),
                        row.hc().toPlainString());
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
     * Result of a successful Timesheet activation.
     */
    public record SyncResult(
            java.util.UUID id,
            LocalDate syncDate,
            short attemptNo,
            String status,
            Integer rowCount,
            String dataHash) {
    }
}
