package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.graph.RstSharePointProperties;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSourceResolver.Source;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetKpiRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetScopeRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncIssueRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Debug entry for Monthly Timesheet sync. Break on {@link TimesheetSyncService#sync(String)}.
 */
class TimesheetSyncServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    private final Map<UUID, TimesheetSyncRun> runStore = new LinkedHashMap<>();
    private final List<TimesheetScope> savedScopes = new ArrayList<>();
    private final List<TimesheetKpi> savedKpis = new ArrayList<>();
    private TimesheetSyncService service;

    @BeforeEach
    void setUp() {
        TimesheetSyncRunRepository syncRuns = proxy(TimesheetSyncRunRepository.class, (proxy, method, args) ->
                switch (method.getName()) {
                    case "saveAndFlush", "save" -> store((TimesheetSyncRun) args[0]);
                    case "findById" -> Optional.ofNullable(runStore.get(args[0]));
                    case "findByKindAndStatus" -> runStore.values().stream()
                            .filter(run -> run.getKind().equals(args[0]) && run.getStatus().equals(args[1]))
                            .findFirst();
                    case "findByKindAndStatusAndSourceDriveItemIdAndSourceEtag" -> Optional.empty();
                    case "findMaxAttemptNo" -> null;
                    case "archiveOtherActive" -> archiveOther((String) args[0], (UUID) args[1], (Instant) args[2]);
                    default -> unsupported(method);
                });
        TimesheetScopeRepository scopes = capturingRepository(TimesheetScopeRepository.class, savedScopes);
        TimesheetKpiRepository kpis = capturingRepository(TimesheetKpiRepository.class, savedKpis);
        TimesheetSyncIssueRepository issues = proxy(TimesheetSyncIssueRepository.class, (proxy, method, args) -> {
            throw new AssertionError("Monthly happy path must not persist issues: " + method.getName());
        });
        TimesheetPersonRepository people = unusedRepository(TimesheetPersonRepository.class);
        TimesheetPositionRepository positions = unusedRepository(TimesheetPositionRepository.class);

        TimesheetSourceResolver sources = new TimesheetSourceResolver(
                new RstSharePointProperties("2.UAT/Data Output/RST"), null) {
            @Override
            public Source open(String kind) {
                assertThat(kind).isEqualTo("MONTHLY");
                return monthlySource();
            }
        };

        service = new TimesheetSyncService(
                new TimesheetReportParser(),
                new TimesheetDailyCalculator(),
                new TimesheetMonthlyCalculator(),
                sources,
                GbsProcessCatalogSource.of(GbsProcessCatalog.allowing("PL3")),
                syncRuns,
                people,
                positions,
                scopes,
                kpis,
                issues,
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                null);
    }

    @Test
    void syncsMonthlyReportIntoActiveScopeAssignmentAndKpi() {
        TimesheetSyncService.SyncResult result = service.sync("MONTHLY");

        assertThat(result.kind()).isEqualTo("MONTHLY");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.syncDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(result.rowCount()).isEqualTo(3);
        assertThat(result.attemptNo()).isEqualTo((short) 1);
        assertThat(result.dataHash()).isNotBlank();

        TimesheetSyncRun run = runStore.get(result.id());
        assertThat(run.getStatus()).isEqualTo("ACTIVE");
        assertThat(run.getCenter()).isEmpty();
        assertThat(run.getSourceDriveItemId()).isEqualTo("drive-monthly-1");
        assertThat(run.getSourceEtag()).isEqualTo("etag-monthly-1");

        assertThat(savedScopes)
                .extracting(
                        TimesheetScope::getSupervisorPositionId,
                        TimesheetScope::getPl3Code,
                        TimesheetScope::getCenter,
                        TimesheetScope::getDomain)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "POS-SUP-1", "PL3", "Kuala Lumpur", "Finance"));
        assertThat(savedKpis)
                .extracting(TimesheetKpi::getSite, TimesheetKpi::getCustomerCountry, TimesheetKpi::getHc)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Site A", "MY", new BigDecimal("2.000000")),
                        org.assertj.core.groups.Tuple.tuple("Site B", "SG", new BigDecimal("1.000000")));
        assertThat(savedScopes).allMatch(scope -> scope.getSyncRunId().equals(result.id()));
        assertThat(savedKpis).allMatch(kpi -> kpi.getSyncRunId().equals(result.id()));
    }

    @Test
    void secondMonthlySyncKeepsOnlyTheLatestComputedRows() {
        TimesheetSyncService.SyncResult first = service.sync("MONTHLY");
        assertThat(savedScopes).isNotEmpty();
        UUID firstId = first.id();

        TimesheetSourceResolver replacement = new TimesheetSourceResolver(
                new RstSharePointProperties("2.UAT/Data Output/RST"), null) {
            @Override
            public Source open(String kind) {
                String csv =
                        """
                        month,emp_emp_id,emp_ccgid,emp_name,emp_email,emp_position_id,supervisor_emp_id,supervisor_ccgid,supervisor_name,supervisor_position_id,sr_manager_emp_id,sr_manager_ccgid,sr_manager_name,sr_manager_position_id,domain_head_emp_id,domain_head_ccgid,domain_head_name,domain_head_position_id,center,site,gbs_domain,pl1,pl2,pl3_code,pl3,carrier,customer_country,hc,management_or_production,cost_type
                        2026-07,EMP-1,S00000001,Agent One,s00000001@dev.local,EMP-POS-1,SUP-1,S00000002,Supervisor One,POS-SUP-1,SRM-1,S00000003,Manager One,POS-SRM-1,DH-1,S00000004,Head One,POS-DH-1,Kuala Lumpur,Site A,Finance,PL1,PL2,PL3,PL3 Name,CMA,MY,3,production,productive
                        """;
                return new Source(
                        "monthly-replacement.csv",
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        "drive-monthly-2",
                        "etag-monthly-2",
                        "SHAREPOINT",
                        LocalDate.of(2026, 7, 31));
            }
        };
        service = new TimesheetSyncService(
                new TimesheetReportParser(),
                new TimesheetDailyCalculator(),
                new TimesheetMonthlyCalculator(),
                replacement,
                GbsProcessCatalogSource.of(GbsProcessCatalog.allowing("PL3")),
                proxy(TimesheetSyncRunRepository.class, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "saveAndFlush", "save" -> store((TimesheetSyncRun) args[0]);
                            case "findById" -> Optional.ofNullable(runStore.get(args[0]));
                            case "findByKindAndStatus" -> runStore.values().stream()
                                    .filter(run -> run.getKind().equals(args[0]) && run.getStatus().equals(args[1]))
                                    .findFirst();
                            case "findByKindAndStatusAndSourceDriveItemIdAndSourceEtag" -> Optional.empty();
                            case "findMaxAttemptNo" -> null;
                            case "archiveOtherActive" ->
                                    archiveOther((String) args[0], (UUID) args[1], (Instant) args[2]);
                            default -> unsupported(method);
                        }),
                unusedRepository(TimesheetPersonRepository.class),
                unusedRepository(TimesheetPositionRepository.class),
                capturingRepository(TimesheetScopeRepository.class, savedScopes),
                capturingRepository(TimesheetKpiRepository.class, savedKpis),
                proxy(TimesheetSyncIssueRepository.class, (proxy, method, args) -> {
                    throw new AssertionError("Replacement Monthly must not persist issues: " + method.getName());
                }),
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                null);

        TimesheetSyncService.SyncResult second = service.sync("MONTHLY");

        assertThat(second.id()).isNotEqualTo(firstId);
        assertThat(runStore.get(firstId).getStatus()).isEqualTo("ARCHIVED");
        assertThat(runStore.get(second.id()).getStatus()).isEqualTo("ACTIVE");
        assertThat(savedScopes).isNotEmpty().allMatch(scope -> scope.getSyncRunId().equals(second.id()));
        assertThat(savedKpis).isNotEmpty().allMatch(row -> row.getSyncRunId().equals(second.id()));
        assertThat(savedKpis).extracting(TimesheetKpi::getHc).containsExactly(new BigDecimal("3.000000"));
    }

    @Test
    void manualUploadReplacesANewerActiveSnapshot() {
        TimesheetSyncService.SyncResult first = service.sync("MONTHLY");
        UUID firstId = first.id();
        assertThat(first.syncDate()).isEqualTo(LocalDate.of(2026, 6, 30));

        String fileName = "older-monthly.csv";
        String csv =
                """
                month,emp_emp_id,emp_ccgid,emp_name,emp_email,emp_position_id,supervisor_emp_id,supervisor_ccgid,supervisor_name,supervisor_position_id,sr_manager_emp_id,sr_manager_ccgid,sr_manager_name,sr_manager_position_id,domain_head_emp_id,domain_head_ccgid,domain_head_name,domain_head_position_id,center,site,gbs_domain,pl1,pl2,pl3_code,pl3,carrier,customer_country,hc,management_or_production,cost_type
                2026-05,EMP-1,S00000001,Agent One,s00000001@dev.local,EMP-POS-1,SUP-1,S00000002,Supervisor One,POS-SUP-1,SRM-1,S00000003,Manager One,POS-SRM-1,DH-1,S00000004,Head One,POS-DH-1,Kuala Lumpur,Site A,Finance,PL1,PL2,PL3,PL3 Name,CMA,MY,4,production,productive
                """;
        TimesheetSourceResolver manual = new TimesheetSourceResolver(
                new RstSharePointProperties("2.UAT/Data Output/RST"), null) {
            @Override
            public Source storeManual(String uploadedName, byte[] content) {
                return new Source(
                        uploadedName,
                        new ByteArrayInputStream(content),
                        "drive-manual-1",
                        "etag-manual-1",
                        "MANUAL",
                        LocalDate.of(2026, 5, 31));
            }
        };
        service = new TimesheetSyncService(
                new TimesheetReportParser(),
                new TimesheetDailyCalculator(),
                new TimesheetMonthlyCalculator(),
                manual,
                GbsProcessCatalogSource.of(GbsProcessCatalog.allowing("PL3")),
                proxy(TimesheetSyncRunRepository.class, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "saveAndFlush", "save" -> store((TimesheetSyncRun) args[0]);
                            case "findById" -> Optional.ofNullable(runStore.get(args[0]));
                            case "findByKindAndStatus" -> runStore.values().stream()
                                    .filter(run -> run.getKind().equals(args[0]) && run.getStatus().equals(args[1]))
                                    .findFirst();
                            case "findByKindAndStatusAndSourceDriveItemIdAndSourceEtag" -> Optional.empty();
                            case "findMaxAttemptNo" -> null;
                            case "archiveOtherActive" ->
                                    archiveOther((String) args[0], (UUID) args[1], (Instant) args[2]);
                            default -> unsupported(method);
                        }),
                unusedRepository(TimesheetPersonRepository.class),
                unusedRepository(TimesheetPositionRepository.class),
                capturingRepository(TimesheetScopeRepository.class, savedScopes),
                capturingRepository(TimesheetKpiRepository.class, savedKpis),
                proxy(TimesheetSyncIssueRepository.class, (proxy, method, args) -> {
                    throw new AssertionError("Manual older Monthly must not persist issues: " + method.getName());
                }),
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                null);

        TimesheetSyncService.SyncResult uploaded =
                service.syncUploaded(fileName, csv.getBytes(StandardCharsets.UTF_8), "LTH001");

        assertThat(uploaded.id()).isNotEqualTo(firstId);
        assertThat(uploaded.status()).isEqualTo("ACTIVE");
        assertThat(uploaded.syncDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(runStore.get(firstId).getStatus()).isEqualTo("ARCHIVED");
        assertThat(runStore.get(uploaded.id()).getSourceType()).isEqualTo("MANUAL");
        assertThat(savedKpis).extracting(TimesheetKpi::getHc).containsExactly(new BigDecimal("4.000000"));
    }

    @Test
    void validationFailureEmailsOnceAndDoesNotCreateASecondRun() {
        AtomicInteger mails = new AtomicInteger();
        List<TimesheetSyncIssue> savedIssues = new ArrayList<>();
        TimesheetSyncRunRepository syncRuns = proxy(TimesheetSyncRunRepository.class, (proxy, method, args) ->
                switch (method.getName()) {
                    case "saveAndFlush", "save" -> store((TimesheetSyncRun) args[0]);
                    case "findById" -> Optional.ofNullable(runStore.get(args[0]));
                    case "findByKindAndStatus" -> runStore.values().stream()
                            .filter(run -> run.getKind().equals(args[0]) && run.getStatus().equals(args[1]))
                            .findFirst();
                    case "findByKindAndStatusAndSourceDriveItemIdAndSourceEtag" -> Optional.empty();
                    case "findMaxAttemptNo" -> null;
                    case "archiveOtherActive" -> archiveOther((String) args[0], (UUID) args[1], (Instant) args[2]);
                    default -> unsupported(method);
                });
        TimesheetSyncIssueRepository issues = proxy(TimesheetSyncIssueRepository.class, (proxy, method, args) ->
                switch (method.getName()) {
                    case "saveAll" -> {
                        @SuppressWarnings("unchecked")
                        Iterable<TimesheetSyncIssue> rows = (Iterable<TimesheetSyncIssue>) args[0];
                        rows.forEach(savedIssues::add);
                        yield savedIssues;
                    }
                    case "findBySyncRunIdOrderBySourceRowAscCreatedAtAsc" -> savedIssues.stream()
                            .filter(issue -> issue.getSyncRunId().equals(args[0]))
                            .toList();
                    default -> unsupported(method);
                });
        TimesheetSourceResolver sources = new TimesheetSourceResolver(
                new RstSharePointProperties("2.UAT/Data Output/RST"), null) {
            @Override
            public Source open(String kind) {
                String csv =
                        """
                        month,emp_emp_id,emp_ccgid,emp_name,emp_email,emp_position_id,supervisor_emp_id,supervisor_ccgid,supervisor_name,supervisor_position_id,sr_manager_emp_id,sr_manager_ccgid,sr_manager_name,sr_manager_position_id,domain_head_emp_id,domain_head_ccgid,domain_head_name,domain_head_position_id,center,site,gbs_domain,pl1,pl2,pl3_code,pl3,carrier,customer_country,hc,management_or_production,cost_type
                        2026-06,EMP-1,S00000001,Agent One,s00000001@dev.local,EMP-POS-1,SUP-1,S00000002,Supervisor One,POS-SUP-1,SRM-1,S00000003,Manager One,POS-SRM-1,DH-1,S00000004,Head One,POS-DH-1,Kuala Lumpur,Site A,Finance,PL1,PL2,PL3,PL3 Name,CMA,MY,1,production,productive
                        """;
                return new Source(
                        "mismatch.csv",
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        "drive-monthly-1",
                        "etag-monthly-1",
                        "SHAREPOINT",
                        LocalDate.of(2026, 1, 31));
            }
        };
        TimesheetSyncAlertNotifier notifier = new TimesheetSyncAlertNotifier(null, null, null, null, null) {
            @Override
            public void notifyFailed(UUID runId) {
                mails.incrementAndGet();
            }
        };
        TimesheetSyncService failing = new TimesheetSyncService(
                new TimesheetReportParser(),
                new TimesheetDailyCalculator(),
                new TimesheetMonthlyCalculator(),
                sources,
                GbsProcessCatalogSource.of(GbsProcessCatalog.allowing("PL3")),
                syncRuns,
                unusedRepository(TimesheetPersonRepository.class),
                unusedRepository(TimesheetPositionRepository.class),
                capturingRepository(TimesheetScopeRepository.class, savedScopes),
                capturingRepository(TimesheetKpiRepository.class, savedKpis),
                issues,
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                notifier);

        assertThatThrownBy(() -> failing.sync("MONTHLY")).isInstanceOf(ApiException.class);
        assertThat(mails.get()).isEqualTo(1);
        assertThat(runStore.values()).hasSize(1).allMatch(run -> "FAILED".equals(run.getStatus()));
    }

    @Test
    void missingFieldWritesIssueAndStillActivates() {
        AtomicInteger mails = new AtomicInteger();
        List<TimesheetSyncIssue> savedIssues = new ArrayList<>();
        TimesheetSourceResolver sources = new TimesheetSourceResolver(
                new RstSharePointProperties("2.UAT/Data Output/RST"), null) {
            @Override
            public Source open(String kind) {
                String csv =
                        """
                        month,emp_emp_id,emp_ccgid,emp_name,emp_email,emp_position_id,supervisor_emp_id,supervisor_ccgid,supervisor_name,supervisor_position_id,sr_manager_emp_id,sr_manager_ccgid,sr_manager_name,sr_manager_position_id,domain_head_emp_id,domain_head_ccgid,domain_head_name,domain_head_position_id,center,site,gbs_domain,pl1,pl2,pl3_code,pl3,carrier,customer_country,hc,management_or_production,cost_type
                        2026-06,EMP-1,S00000001,Agent One,s00000001@dev.local,EMP-POS-1,SUP-1,S00000002,Supervisor One,POS-SUP-1,SRM-1,S00000003,Manager One,POS-SRM-1,DH-1,S00000004,Head One,POS-DH-1,Kuala Lumpur,Site A,Finance,PL1,PL2,PL3,PL3 Name,CMA,MY,1,production,productive
                        2026-06,EMP-2,S00000005,Agent Two,s00000005@dev.local,EMP-POS-2,SUP-1,S00000002,Supervisor One,POS-SUP-1,SRM-1,S00000003,Manager One,POS-SRM-1,DH-1,S00000004,Head One,POS-DH-1,Kuala Lumpur,Site B,Finance,PL1,PL2,,PL3 Name,CMA,SG,1,production,productive
                        """;
                return new Source(
                        "missing-field.csv",
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        "drive-monthly-3",
                        "etag-monthly-3",
                        "SHAREPOINT",
                        LocalDate.of(2026, 6, 30));
            }
        };
        TimesheetSyncAlertNotifier notifier = new TimesheetSyncAlertNotifier(null, null, null, null, null) {
            @Override
            public void notifyFailed(UUID runId) {
                mails.incrementAndGet();
            }
        };
        TimesheetSyncService tolerant = new TimesheetSyncService(
                new TimesheetReportParser(),
                new TimesheetDailyCalculator(),
                new TimesheetMonthlyCalculator(),
                sources,
                GbsProcessCatalogSource.of(GbsProcessCatalog.allowing("PL3")),
                proxy(TimesheetSyncRunRepository.class, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "saveAndFlush", "save" -> store((TimesheetSyncRun) args[0]);
                            case "findById" -> Optional.ofNullable(runStore.get(args[0]));
                            case "findByKindAndStatus" -> runStore.values().stream()
                                    .filter(run -> run.getKind().equals(args[0]) && run.getStatus().equals(args[1]))
                                    .findFirst();
                            case "findByKindAndStatusAndSourceDriveItemIdAndSourceEtag" -> Optional.empty();
                            case "findMaxAttemptNo" -> null;
                            case "archiveOtherActive" -> archiveOther((String) args[0], (UUID) args[1], (Instant) args[2]);
                            default -> unsupported(method);
                        }),
                unusedRepository(TimesheetPersonRepository.class),
                unusedRepository(TimesheetPositionRepository.class),
                capturingRepository(TimesheetScopeRepository.class, savedScopes),
                capturingRepository(TimesheetKpiRepository.class, savedKpis),
                proxy(TimesheetSyncIssueRepository.class, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "saveAll" -> {
                                @SuppressWarnings("unchecked")
                                Iterable<TimesheetSyncIssue> rows = (Iterable<TimesheetSyncIssue>) args[0];
                                rows.forEach(savedIssues::add);
                                yield savedIssues;
                            }
                            default -> unsupported(method);
                        }),
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                notifier);

        TimesheetSyncService.SyncResult result = tolerant.sync("MONTHLY");

        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(mails.get()).isZero();
        assertThat(savedIssues).extracting(TimesheetSyncIssue::getCode).containsExactly("MISSING_FIELD");
        assertThat(savedScopes).extracting(TimesheetScope::getSupervisorPositionId).containsExactly("POS-SUP-1");
    }

    private TimesheetSyncRun store(TimesheetSyncRun run) {
        runStore.put(run.getId(), run);
        return run;
    }

    private int archiveOther(String kind, UUID keepRunId, Instant completedAt) {
        int archived = 0;
        for (TimesheetSyncRun run : runStore.values()) {
            if (kind.equals(run.getKind()) && "ACTIVE".equals(run.getStatus()) && !keepRunId.equals(run.getId())) {
                run.markArchived(completedAt);
                archived++;
            }
        }
        return archived;
    }

    private static Source monthlySource() {
        InputStream content = TimesheetSyncServiceTests.class.getResourceAsStream("/timesheet/monthly-sample.csv");
        assertThat(content).as("monthly-sample.csv").isNotNull();
        return new Source(
                "monthly-sample.csv",
                content,
                "drive-monthly-1",
                "etag-monthly-1",
                "SHAREPOINT",
                LocalDate.of(2026, 6, 30));
    }

    private static PlatformTransactionManager noOpTransactions() {
        return proxy(PlatformTransactionManager.class, (proxy, method, args) ->
                switch (method.getName()) {
                    case "getTransaction" -> new SimpleTransactionStatus();
                    case "commit", "rollback" -> null;
                    default -> unsupported(method);
                });
    }

    private static <T, R> T capturingRepository(Class<T> type, List<R> target) {
        return proxy(type, (proxy, method, args) -> {
            if ("saveAll".equals(method.getName())) {
                @SuppressWarnings("unchecked")
                Iterable<R> rows = (Iterable<R>) args[0];
                rows.forEach(target::add);
                return target;
            }
            if ("deleteBySyncRunIdNot".equals(method.getName())) {
                UUID keepRunId = (UUID) args[0];
                target.removeIf(row -> !keepRunId.equals(syncRunIdOf(row)));
                return target.size();
            }
            return unsupported(method);
        });
    }

    private static UUID syncRunIdOf(Object row) {
        return switch (row) {
            case TimesheetScope scope -> scope.getSyncRunId();
            case TimesheetKpi kpi -> kpi.getSyncRunId();
            default -> throw new AssertionError("Unexpected computed row: " + row);
        };
    }

    private static <T> T unusedRepository(Class<T> type) {
        return proxy(type, (proxy, method, args) -> {
            throw new AssertionError("Monthly sync must not write Daily tables: " + method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object unsupported(Method method) {
        throw new AssertionError("Unexpected call: " + method);
    }
}
