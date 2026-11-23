package com.cmacgm.gbs.rst.api.sourcedata.application;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cmacgm.gbs.rst.api.audit.application.AuditRecorder;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstCenters;
import com.cmacgm.gbs.rst.api.sourcedata.application.SharedKpiCalculator.SiteRef;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterSource;
import com.cmacgm.gbs.rst.api.sourcedata.domain.GbsSiteCode;
import com.cmacgm.gbs.rst.api.sourcedata.persistence.CenterDeliveryHcRepository;
import com.cmacgm.gbs.rst.api.sourcedata.persistence.GbsSiteCodeRepository;
import com.cmacgm.gbs.rst.api.timesheet.application.GbsProcessCatalogSource;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSourceResolver;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceIssue;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceSnapshot;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionParentRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgSeatRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.ProcessScopeRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SourceIssueRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SourceSnapshotRepository;

/**
 * Imports one global MyHR or SKPI file. A successful MyHR file becomes the
 * current snapshot. A successful SKPI file becomes current when its period is
 * the same or later than the current one; an earlier period stays in history.
 * Row defects are stored and do not block a file that has one period.
 */
@Service
public class SourceDataImportService {

    private static final Logger log = LoggerFactory.getLogger(SourceDataImportService.class);
    private static final String GLOBAL = "";

    private final MyHrWorkbookParser myHrParser;
    private final SharedKpiWorkbookParser skpiParser;
    private final MyHrOrgCalculator myHrCalculator;
    private final SharedKpiCalculator skpiCalculator;
    private final CenterSourceService centers;
    private final GbsSiteCodeRepository siteCodes;
    private final GbsProcessCatalogSource processCatalogs;
    private final SourceSnapshotRepository snapshots;
    private final SourceIssueRepository issues;
    private final OrgPersonRepository people;
    private final OrgPositionRepository positions;
    private final OrgPositionParentRepository parents;
    private final OrgSeatRepository seats;
    private final ProcessScopeRepository processes;
    private final CenterDeliveryHcRepository deliveryHc;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final AuditRecorder audits;
    private final TimesheetSourceResolver sources;

    /**
     * @param myHrParser MyHR workbook
     * @param skpiParser SKPI workbook
     * @param myHrCalculator MyHR org compute
     * @param skpiCalculator SKPI compute
     * @param centers center source mode
     * @param siteCodes site map
     * @param processCatalogs GBS Process
     * @param snapshots snapshot headers
     * @param issues row issues
     * @param people people
     * @param positions positions
     * @param parents parent edges
     * @param seats seats
     * @param processes process scope
     * @param deliveryHc center Delivery HC
     * @param transactionManager activation transaction
     * @param clock timestamps
     * @param audits upload audit
     * @param sources original file storage
     */
    public SourceDataImportService(
            MyHrWorkbookParser myHrParser,
            SharedKpiWorkbookParser skpiParser,
            MyHrOrgCalculator myHrCalculator,
            SharedKpiCalculator skpiCalculator,
            CenterSourceService centers,
            GbsSiteCodeRepository siteCodes,
            GbsProcessCatalogSource processCatalogs,
            SourceSnapshotRepository snapshots,
            SourceIssueRepository issues,
            OrgPersonRepository people,
            OrgPositionRepository positions,
            OrgPositionParentRepository parents,
            OrgSeatRepository seats,
            ProcessScopeRepository processes,
            CenterDeliveryHcRepository deliveryHc,
            PlatformTransactionManager transactionManager,
            Clock clock,
            AuditRecorder audits,
            TimesheetSourceResolver sources) {
        this.myHrParser = myHrParser;
        this.skpiParser = skpiParser;
        this.myHrCalculator = myHrCalculator;
        this.skpiCalculator = skpiCalculator;
        this.centers = centers;
        this.siteCodes = siteCodes;
        this.processCatalogs = processCatalogs;
        this.snapshots = snapshots;
        this.issues = issues;
        this.people = people;
        this.positions = positions;
        this.parents = parents;
        this.seats = seats;
        this.processes = processes;
        this.deliveryHc = deliveryHc;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.audits = audits;
        this.sources = sources;
    }

    private record StoredFile(String fileName, String driveItemId, String etag) {
    }

    /**
     * Outcome of one upload.
     *
     * @param id snapshot id
     * @param kind MYHR or SHARED_KPI
     * @param status ACTIVE, or ARCHIVED when an SKPI period is already newer
     * @param syncDate business date
     * @param period SKPI period, otherwise null
     * @param rowCount stored rows
     * @param issueCount row issues kept with the snapshot
     */
    public record ImportResult(
            UUID id,
            String kind,
            String status,
            LocalDate syncDate,
            String period,
            Integer rowCount,
            int issueCount) {
    }

    /**
     * Imports a global MyHR file.
     *
     * @param fileName original name
     * @param content xlsx bytes
     * @return active snapshot
     */
    public ImportResult importMyHr(String fileName, byte[] content) {
        String kind = "MYHR";
        guard(kind);
        byte[] bytes = content == null ? new byte[0] : content;
        StoredFile stored = store("MyHR", fileName, bytes);
        MyHrWorkbookParser.Parsed parsed = parse(kind, stored, () -> myHrParser.parse(new ByteArrayInputStream(bytes)));
        LocalDate syncDate = MyHrOrgCalculator.syncDate(parsed.people(), LocalDate.now(clock));
        SourceSnapshot run = start(kind, syncDate, null, stored);
        try {
            MyHrOrgCalculator.Result computed = myHrCalculator.compute(
                    run.getId(),
                    parsed.people(),
                    parsed.positions(),
                    centers.centersWith(CenterSource.MYHR_SKPI),
                    clock.instant());
            if (!computed.activate()) {
                reject(run, computed.issues());
            }
            saveIssues(computed.issues());
            people.saveAll(computed.people());
            positions.saveAll(computed.positions());
            parents.saveAll(computed.parents());
            seats.saveAll(computed.seats());
            return activate(run, computed.people().size(), hash(bytes), computed.issues().size(), false);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return failUnexpected(kind, run, ex);
        }
    }

    /**
     * Imports a global SKPI file. Offices are mapped to site codes before insert.
     *
     * @param fileName original name
     * @param content xlsx bytes
     * @return active snapshot
     */
    public ImportResult importSharedKpi(String fileName, byte[] content) {
        String kind = "SHARED_KPI";
        guard(kind);
        byte[] bytes = content == null ? new byte[0] : content;
        StoredFile stored = store("SharedKpi", fileName, bytes);
        List<SharedKpiWorkbookParser.KpiRow> rows = parse(
                kind, stored, () -> skpiParser.parse(new ByteArrayInputStream(bytes)));
        String periodError = SharedKpiCalculator.periodError(rows);
        if (periodError != null) {
            failWithoutRows(kind, stored, TimesheetSyncErrorCode.PERIOD_MISMATCH.code(), periodError);
            throw new ApiException(HttpStatus.CONFLICT, TimesheetSyncErrorCode.PERIOD_MISMATCH.code(), periodError);
        }
        String period = SharedKpiCalculator.period(rows);
        SourceSnapshot run = start(
                kind, SharedKpiCalculator.syncDate(period, LocalDate.now(clock)), period, stored);
        try {
            SharedKpiCalculator.Result computed = skpiCalculator.compute(
                    run.getId(),
                    rows,
                    siteMap(),
                    centers.centersWith(CenterSource.MYHR_SKPI),
                    processCatalogs.loadDetailed(),
                    clock.instant());
            if (!computed.activate()) {
                reject(run, computed.issues());
            }
            saveIssues(computed.issues());
            processes.saveAll(computed.processes());
            deliveryHc.saveAll(computed.lines());
            return activate(run, computed.lines().size(), hash(bytes), computed.issues().size(), true);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return failUnexpected(kind, run, ex);
        }
    }

    private Map<String, SiteRef> siteMap() {
        Map<String, SiteRef> sites = new LinkedHashMap<>();
        for (GbsSiteCode row : siteCodes.findAll()) {
            String office = SourceValues.upper(row.getGbsSite(), 80);
            String center = RstCenters.canonicalize(row.getCenter());
            if (office == null || center == null) {
                continue;
            }
            sites.put(office, new SiteRef(row.getSiteCode(), center));
        }
        return sites;
    }

    private void guard(String kind) {
        if (snapshots.findByKindAndStatusAndCenter(kind, "LOADING", GLOBAL).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.SYNC_IN_PROGRESS.code(),
                    "A " + kind + " import is already running.");
        }
    }

    private StoredFile store(String module, String fileName, byte[] bytes) {
        TimesheetSourceResolver.Source stored = sources.storeManualModule(module, safeName(fileName), bytes);
        String name = stored.fileName() == null || stored.fileName().isBlank() ? safeName(fileName) : stored.fileName();
        return new StoredFile(name, stored.driveItemId(), stored.etag());
    }

    private static String safeName(String fileName) {
        return fileName == null || fileName.isBlank() ? "upload.xlsx" : fileName.trim();
    }

    private <T> T parse(String kind, StoredFile stored, ParseCall<T> call) {
        try {
            return call.parse();
        } catch (ApiException ex) {
            failWithoutRows(kind, stored, ex.code(), ex.getMessage());
            throw ex;
        }
    }

    private SourceSnapshot start(String kind, LocalDate syncDate, String period, StoredFile stored) {
        Instant now = clock.instant();
        SourceSnapshot run = SourceSnapshot.startLoading(kind, syncDate, nextAttempt(kind, syncDate), now);
        run.setCenter(GLOBAL);
        run.setPeriod(period);
        run.setSource(stored.driveItemId(), stored.etag(), "MANUAL", stored.fileName());
        snapshots.saveAndFlush(run);
        run.setLatestAuditEventId(audits.recordSync(run.getId()).getId());
        snapshots.save(run);
        return run;
    }

    private void reject(SourceSnapshot run, List<SourceIssue> rowIssues) {
        saveIssues(rowIssues);
        SourceIssue first = rowIssues.isEmpty() ? null : rowIssues.getFirst();
        String code = first == null ? TimesheetSyncErrorCode.EMPTY_FILE.code() : first.getCode();
        String message = first == null ? "No rows remained after filtering." : first.getMessage();
        markFailed(run.getId(), code, message);
        throw new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ImportResult activate(
            SourceSnapshot run, int rowCount, String dataHash, int issueCount, boolean byPeriod) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = clock.instant();
            if (byPeriod && !replacesActive(run)) {
                SourceSnapshot current = reload(run.getId());
                current.markKept(rowCount, dataHash, now);
                snapshots.saveAndFlush(current);
                return;
            }
            snapshots.archiveOtherActive(run.getKind(), GLOBAL, run.getId(), now);
            SourceSnapshot current = reload(run.getId());
            current.markActive(rowCount, dataHash, now);
            snapshots.saveAndFlush(current);
        });
        SourceSnapshot active = snapshots.findById(run.getId()).orElseThrow();
        log.info(
                "Source {} {}: id={} period={} rows={} issues={}",
                active.getKind(),
                active.getStatus(),
                active.getId(),
                active.getPeriod(),
                active.getRowCount(),
                issueCount);
        return new ImportResult(
                active.getId(),
                active.getKind(),
                active.getStatus(),
                active.getSyncDate(),
                active.getPeriod(),
                active.getRowCount(),
                issueCount);
    }

    private SourceSnapshot reload(UUID runId) {
        return snapshots.findById(runId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        TimesheetSyncErrorCode.COUNT_MISMATCH.code(),
                        "Import disappeared before activation."));
    }

    private boolean replacesActive(SourceSnapshot incoming) {
        return snapshots.findByKindAndStatusAndCenter(incoming.getKind(), "ACTIVE", GLOBAL)
                .map(active -> SharedKpiCalculator.replacesCurrent(active.getPeriod(), incoming.getPeriod()))
                .orElse(true);
    }

    private void saveIssues(List<SourceIssue> rowIssues) {
        if (rowIssues != null && !rowIssues.isEmpty()) {
            issues.saveAll(rowIssues);
        }
    }

    private void failWithoutRows(String kind, StoredFile stored, String code, String message) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        SourceSnapshot run = SourceSnapshot.startLoading(kind, today, nextAttempt(kind, today), now);
        run.setCenter(GLOBAL);
        run.setSource(stored.driveItemId(), stored.etag(), "MANUAL", stored.fileName());
        run.markFailed(code, sanitize(message), now);
        snapshots.save(run);
        run.setLatestAuditEventId(audits.recordSync(run.getId()).getId());
        snapshots.save(run);
        issues.save(SourceIssue.error(run.getId(), code, sanitize(message), null, null, null, null, null, now));
    }

    private ImportResult failUnexpected(String kind, SourceSnapshot run, RuntimeException ex) {
        log.error("Source {} import failed: id={}", kind, run.getId(), ex);
        String message = "Import failed unexpectedly.";
        markFailed(run.getId(), TimesheetSyncErrorCode.COUNT_MISMATCH.code(), message);
        throw new ApiException(HttpStatus.CONFLICT, TimesheetSyncErrorCode.COUNT_MISMATCH.code(), message);
    }

    private void markFailed(UUID runId, String code, String message) {
        transactionTemplate.executeWithoutResult(status -> snapshots.findById(runId).ifPresent(run -> {
            run.markFailed(code, sanitize(message), clock.instant());
            snapshots.save(run);
        }));
    }

    private short nextAttempt(String kind, LocalDate syncDate) {
        Short max = snapshots.findMaxAttemptNo(kind, GLOBAL, syncDate);
        int next = (max == null ? 0 : max) + 1;
        if (next > Short.MAX_VALUE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.COUNT_MISMATCH.code(),
                    "Too many imports for " + kind + " " + syncDate);
        }
        return (short) next;
    }

    private static String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Import failed.";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("duplicate key") || lower.contains("batch entry") || lower.contains("sql [")) {
            return "Import failed unexpectedly.";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    @FunctionalInterface
    private interface ParseCall<T> {
        T parse();
    }
}
