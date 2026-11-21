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

import com.cmacgm.gbs.rst.api.audit.application.AuditRecorder;
import com.cmacgm.gbs.rst.api.audit.domain.AuditAction;
import com.cmacgm.gbs.rst.api.audit.domain.AuditEntityType;
import com.cmacgm.gbs.rst.api.audit.domain.AuditEvent;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetSharePointProperties;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSourceResolver.Source;
import com.cmacgm.gbs.rst.api.timesheet.domain.ProcessScope;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceIssue;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceSnapshot;
import com.cmacgm.gbs.rst.api.timesheet.domain.SupervisorDeliveryHc;
import com.cmacgm.gbs.rst.api.timesheet.domain.SupervisorProcess;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionParentRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgSeatRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.ProcessScopeRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SupervisorDeliveryHcRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SupervisorProcessRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SourceIssueRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SourceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Debug entry for Monthly Timesheet sync. Break on {@link TimesheetSyncService#sync(String)}.
 */
class TimesheetSyncServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    private final Map<UUID, SourceSnapshot> runStore = new LinkedHashMap<>();
    private final List<SupervisorProcess> savedScopes = new ArrayList<>();
    private final List<ProcessScope> savedProcesses = new ArrayList<>();
    private final List<SupervisorDeliveryHc> savedKpis = new ArrayList<>();
    private TimesheetSyncService service;

    @BeforeEach
    void setUp() {
        SourceSnapshotRepository syncRuns = syncRunRepository();
        SupervisorProcessRepository scopes = capturingRepository(SupervisorProcessRepository.class, savedScopes);
        ProcessScopeRepository processes = capturingRepository(ProcessScopeRepository.class, savedProcesses);
        SupervisorDeliveryHcRepository kpis = capturingRepository(SupervisorDeliveryHcRepository.class, savedKpis);
        SourceIssueRepository issues = proxy(SourceIssueRepository.class, (proxy, method, args) -> {
            throw new AssertionError("Monthly happy path must not persist issues: " + method.getName());
        });
        OrgPersonRepository people = unusedRepository(OrgPersonRepository.class);
        OrgPositionRepository positions = unusedRepository(OrgPositionRepository.class);

        TimesheetSourceResolver sources = new TimesheetSourceResolver(
                new TimesheetSharePointProperties(null, null, "4.RST/2.UAT"), null) {
            @Override
            public java.util.List<Source> openAll(String kind) {
                assertThat(kind).isEqualTo("MONTHLY");
                return java.util.List.of(monthlySource());
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
                unusedRepository(OrgPositionParentRepository.class),
                unusedRepository(OrgSeatRepository.class),
                scopes,
                processes,
                kpis,
                issues,
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                null,
                stubAudits());
    }

    @Test
    void syncsMonthlyReportIntoActiveScopeAssignmentAndKpi() {
        TimesheetSyncService.SyncResult result = service.sync("MONTHLY").getFirst();

        assertThat(result.kind()).isEqualTo("MONTHLY");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.syncDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(result.rowCount()).isEqualTo(3);
        assertThat(result.attemptNo()).isEqualTo((short) 1);
        assertThat(result.dataHash()).isNotBlank();

        SourceSnapshot run = runStore.get(result.id());
        assertThat(run.getStatus()).isEqualTo("ACTIVE");
        assertThat(run.getCenter()).isEqualTo("GBS INDIA");
        assertThat(run.getSourceDriveItemId()).isEqualTo("drive-monthly-1");
        assertThat(run.getSourceEtag()).isEqualTo("etag-monthly-1");

        assertThat(savedScopes)
                .extracting(
                        SupervisorProcess::getSupervisorPositionId,
                        SupervisorProcess::getPl3Code,
                        SupervisorProcess::getCenter)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "POS-SUP-1", "PL3", "GBS INDIA"));
        assertThat(savedProcesses)
                .extracting(ProcessScope::getCenter, ProcessScope::getPl3Code, ProcessScope::getDomain)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("GBS INDIA", "PL3", "Finance"));
        assertThat(savedKpis)
                .extracting(SupervisorDeliveryHc::getSite, SupervisorDeliveryHc::getCustomerCountry, SupervisorDeliveryHc::getHc)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Site A", "MY", new BigDecimal("2.000000")),
                        org.assertj.core.groups.Tuple.tuple("Site B", "SG", new BigDecimal("1.000000")));
        assertThat(savedScopes).allMatch(scope -> scope.getSyncRunId().equals(result.id()));
        assertThat(savedKpis).allMatch(kpi -> kpi.getSyncRunId().equals(result.id()));
    }

    @Test
    void secondCenterKeepsTheOtherActiveSnapshot() {
        TimesheetSyncService.SyncResult india = service.sync("MONTHLY").getFirst();
        TimesheetSourceResolver china = new TimesheetSourceResolver(
                new TimesheetSharePointProperties(null, null, "4.RST/2.UAT"), null) {
            @Override
            public java.util.List<Source> openAll(String kind) {
                String csv =
                        """
                        month,emp_emp_id,emp_ccgid,emp_name,emp_email,emp_position_id,supervisor_emp_id,supervisor_ccgid,supervisor_name,supervisor_position_id,sr_manager_emp_id,sr_manager_ccgid,sr_manager_name,sr_manager_position_id,domain_head_emp_id,domain_head_ccgid,domain_head_name,domain_head_position_id,center,site,gbs_domain,pl1,pl2,pl3_code,pl3,carrier,customer_country,hc,management_or_production,cost_type
                        2026-06,EMP-9,S00000009,Agent Nine,s00000009@dev.local,EMP-POS-9,SUP-9,S00000008,Supervisor Nine,POS-SUP-9,SRM-9,S00000007,Manager Nine,POS-SRM-9,DH-9,S00000006,Head Nine,POS-DH-9,GBS CHINA,Site C,Finance,PL1,PL2,PL3,PL3 Name,CMA,CN,5,production,productive
                        """;
                return java.util.List.of(new Source(
                        "Monthly Report of 202606(GBS CHINA).csv",
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        "drive-monthly-cn",
                        "etag-monthly-cn",
                        "SHAREPOINT",
                        LocalDate.of(2026, 6, 30)));
            }
        };
        service = new TimesheetSyncService(
                new TimesheetReportParser(),
                new TimesheetDailyCalculator(),
                new TimesheetMonthlyCalculator(),
                china,
                GbsProcessCatalogSource.of(GbsProcessCatalog.allowing("PL3")),
                syncRunRepository(),
                unusedRepository(OrgPersonRepository.class),
                unusedRepository(OrgPositionRepository.class),
                unusedRepository(OrgPositionParentRepository.class),
                unusedRepository(OrgSeatRepository.class),
                capturingRepository(SupervisorProcessRepository.class, savedScopes),
                capturingRepository(ProcessScopeRepository.class, savedProcesses),
                capturingRepository(SupervisorDeliveryHcRepository.class, savedKpis),
                proxy(SourceIssueRepository.class, (proxy, method, args) -> {
                    throw new AssertionError("China Monthly must not persist issues: " + method.getName());
                }),
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                null,
                stubAudits());

        TimesheetSyncService.SyncResult chinaResult = service.sync("MONTHLY").getFirst();

        assertThat(chinaResult.id()).isNotEqualTo(india.id());
        assertThat(runStore.get(india.id()).getStatus()).isEqualTo("ACTIVE");
        assertThat(runStore.get(chinaResult.id()).getStatus()).isEqualTo("ACTIVE");
        assertThat(runStore.get(chinaResult.id()).getCenter()).isEqualTo("GBS CHINA");
        assertThat(savedScopes)
                .extracting(SupervisorProcess::getCenter)
                .contains("GBS INDIA", "GBS CHINA");
    }

    @Test
    void secondMonthlySyncKeepsOnlyTheLatestComputedRows() {
        TimesheetSyncService.SyncResult first = service.sync("MONTHLY").getFirst();
        assertThat(savedScopes).isNotEmpty();
        UUID firstId = first.id();

        TimesheetSourceResolver replacement = new TimesheetSourceResolver(
                new TimesheetSharePointProperties(null, null, "4.RST/2.UAT"), null) {
            @Override
            public java.util.List<Source> openAll(String kind) {
                String csv =
                        """
                        month,emp_emp_id,emp_ccgid,emp_name,emp_email,emp_position_id,supervisor_emp_id,supervisor_ccgid,supervisor_name,supervisor_position_id,sr_manager_emp_id,sr_manager_ccgid,sr_manager_name,sr_manager_position_id,domain_head_emp_id,domain_head_ccgid,domain_head_name,domain_head_position_id,center,site,gbs_domain,pl1,pl2,pl3_code,pl3,carrier,customer_country,hc,management_or_production,cost_type
                        2026-07,EMP-1,S00000001,Agent One,s00000001@dev.local,EMP-POS-1,SUP-1,S00000002,Supervisor One,POS-SUP-1,SRM-1,S00000003,Manager One,POS-SRM-1,DH-1,S00000004,Head One,POS-DH-1,GBS INDIA,Site A,Finance,PL1,PL2,PL3,PL3 Name,CMA,MY,3,production,productive
                        """;
                return java.util.List.of(new Source(
                        "Monthly Report of 202607(GBS INDIA).csv",
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        "drive-monthly-2",
                        "etag-monthly-2",
                        "SHAREPOINT",
                        LocalDate.of(2026, 7, 31)));
            }
        };
        service = new TimesheetSyncService(
                new TimesheetReportParser(),
                new TimesheetDailyCalculator(),
                new TimesheetMonthlyCalculator(),
                replacement,
                GbsProcessCatalogSource.of(GbsProcessCatalog.allowing("PL3")),
                proxy(SourceSnapshotRepository.class, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "saveAndFlush", "save" -> store((SourceSnapshot) args[0]);
                            case "findById" -> Optional.ofNullable(runStore.get(args[0]));
                            case "findByKindAndStatus" -> runStore.values().stream()
                                    .filter(run -> run.getKind().equals(args[0]) && run.getStatus().equals(args[1]))
                                    .toList();
                            case "findByKindAndStatusAndCenter" -> runStore.values().stream()
                                    .filter(run -> run.getKind().equals(args[0])
                                            && run.getStatus().equals(args[1])
                                            && run.getCenter().equals(args[2]))
                                    .findFirst();
                            case "findByKindAndStatusAndSourceDriveItemIdAndSourceEtag" -> Optional.empty();
                            case "findMaxAttemptNo" -> null;
                            case "archiveOtherActive" -> archiveOther(
                                    (String) args[0], (String) args[1], (UUID) args[2], (Instant) args[3]);
                            default -> unsupported(method);
                        }),
                unusedRepository(OrgPersonRepository.class),
                unusedRepository(OrgPositionRepository.class),
                unusedRepository(OrgPositionParentRepository.class),
                unusedRepository(OrgSeatRepository.class),
                capturingRepository(SupervisorProcessRepository.class, savedScopes),
                capturingRepository(ProcessScopeRepository.class, savedProcesses),
                capturingRepository(SupervisorDeliveryHcRepository.class, savedKpis),
                proxy(SourceIssueRepository.class, (proxy, method, args) -> {
                    throw new AssertionError("Replacement Monthly must not persist issues: " + method.getName());
                }),
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                null,
                stubAudits());

        TimesheetSyncService.SyncResult second = service.sync("MONTHLY").getFirst();

        assertThat(second.id()).isNotEqualTo(firstId);
        assertThat(runStore.get(firstId).getStatus()).isEqualTo("ARCHIVED");
        assertThat(runStore.get(second.id()).getStatus()).isEqualTo("ACTIVE");
        assertThat(savedScopes).isNotEmpty().allMatch(scope -> scope.getSyncRunId().equals(second.id()));
        assertThat(savedKpis).isNotEmpty().allMatch(row -> row.getSyncRunId().equals(second.id()));
        assertThat(savedKpis).extracting(SupervisorDeliveryHc::getHc).containsExactly(new BigDecimal("3.000000"));
    }

    @Test
    void manualUploadReplacesANewerActiveSnapshot() {
        TimesheetSyncService.SyncResult first = service.sync("MONTHLY").getFirst();
        UUID firstId = first.id();
        assertThat(first.syncDate()).isEqualTo(LocalDate.of(2026, 6, 30));

        String fileName = "Monthly Report of 202605(GBS INDIA).csv";
        String csv =
                """
                month,emp_emp_id,emp_ccgid,emp_name,emp_email,emp_position_id,supervisor_emp_id,supervisor_ccgid,supervisor_name,supervisor_position_id,sr_manager_emp_id,sr_manager_ccgid,sr_manager_name,sr_manager_position_id,domain_head_emp_id,domain_head_ccgid,domain_head_name,domain_head_position_id,center,site,gbs_domain,pl1,pl2,pl3_code,pl3,carrier,customer_country,hc,management_or_production,cost_type
                2026-05,EMP-1,S00000001,Agent One,s00000001@dev.local,EMP-POS-1,SUP-1,S00000002,Supervisor One,POS-SUP-1,SRM-1,S00000003,Manager One,POS-SRM-1,DH-1,S00000004,Head One,POS-DH-1,GBS INDIA,Site A,Finance,PL1,PL2,PL3,PL3 Name,CMA,MY,4,production,productive
                """;
        TimesheetSourceResolver manual = new TimesheetSourceResolver(
                new TimesheetSharePointProperties(null, null, "4.RST/2.UAT"), null) {
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
                proxy(SourceSnapshotRepository.class, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "saveAndFlush", "save" -> store((SourceSnapshot) args[0]);
                            case "findById" -> Optional.ofNullable(runStore.get(args[0]));
                            case "findByKindAndStatus" -> runStore.values().stream()
                                    .filter(run -> run.getKind().equals(args[0]) && run.getStatus().equals(args[1]))
                                    .toList();
                            case "findByKindAndStatusAndCenter" -> runStore.values().stream()
                                    .filter(run -> run.getKind().equals(args[0])
                                            && run.getStatus().equals(args[1])
                                            && run.getCenter().equals(args[2]))
                                    .findFirst();
                            case "findByKindAndStatusAndSourceDriveItemIdAndSourceEtag" -> Optional.empty();
                            case "findMaxAttemptNo" -> null;
                            case "archiveOtherActive" -> archiveOther(
                                    (String) args[0], (String) args[1], (UUID) args[2], (Instant) args[3]);
                            default -> unsupported(method);
                        }),
                unusedRepository(OrgPersonRepository.class),
                unusedRepository(OrgPositionRepository.class),
                unusedRepository(OrgPositionParentRepository.class),
                unusedRepository(OrgSeatRepository.class),
                capturingRepository(SupervisorProcessRepository.class, savedScopes),
                capturingRepository(ProcessScopeRepository.class, savedProcesses),
                capturingRepository(SupervisorDeliveryHcRepository.class, savedKpis),
                proxy(SourceIssueRepository.class, (proxy, method, args) -> {
                    throw new AssertionError("Manual older Monthly must not persist issues: " + method.getName());
                }),
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                null,
                stubAudits());

        TimesheetSyncService.SyncResult uploaded =
                service.syncUploaded(fileName, csv.getBytes(StandardCharsets.UTF_8));

        assertThat(uploaded.id()).isNotEqualTo(firstId);
        assertThat(uploaded.status()).isEqualTo("ACTIVE");
        assertThat(uploaded.syncDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(runStore.get(firstId).getStatus()).isEqualTo("ARCHIVED");
        assertThat(runStore.get(uploaded.id()).getSourceType()).isEqualTo("MANUAL");
        assertThat(savedKpis).extracting(SupervisorDeliveryHc::getHc).containsExactly(new BigDecimal("4.000000"));
    }

    @Test
    void validationFailureEmailsOnceAndDoesNotCreateASecondRun() {
        AtomicInteger mails = new AtomicInteger();
        List<SourceIssue> savedIssues = new ArrayList<>();
        SourceSnapshotRepository syncRuns = proxy(SourceSnapshotRepository.class, (proxy, method, args) ->
                switch (method.getName()) {
                    case "saveAndFlush", "save" -> store((SourceSnapshot) args[0]);
                    case "findById" -> Optional.ofNullable(runStore.get(args[0]));
                    case "findByKindAndStatus" -> runStore.values().stream()
                            .filter(run -> run.getKind().equals(args[0]) && run.getStatus().equals(args[1]))
                            .toList();
                    case "findByKindAndStatusAndCenter" -> runStore.values().stream()
                            .filter(run -> run.getKind().equals(args[0])
                                    && run.getStatus().equals(args[1])
                                    && run.getCenter().equals(args[2]))
                            .findFirst();
                    case "findByKindAndStatusAndSourceDriveItemIdAndSourceEtag" -> Optional.empty();
                    case "findMaxAttemptNo" -> null;
                    case "archiveOtherActive" -> archiveOther(
                            (String) args[0], (String) args[1], (UUID) args[2], (Instant) args[3]);
                    default -> unsupported(method);
                });
        SourceIssueRepository issues = proxy(SourceIssueRepository.class, (proxy, method, args) ->
                switch (method.getName()) {
                    case "saveAll" -> {
                        @SuppressWarnings("unchecked")
                        Iterable<SourceIssue> rows = (Iterable<SourceIssue>) args[0];
                        rows.forEach(savedIssues::add);
                        yield savedIssues;
                    }
                    case "findBySyncRunIdOrderBySourceRowAscCreatedAtAsc" -> savedIssues.stream()
                            .filter(issue -> issue.getSyncRunId().equals(args[0]))
                            .toList();
                    default -> unsupported(method);
                });
        TimesheetSourceResolver sources = new TimesheetSourceResolver(
                new TimesheetSharePointProperties(null, null, "4.RST/2.UAT"), null) {
            @Override
            public java.util.List<Source> openAll(String kind) {
                String csv =
                        """
                        month,emp_emp_id,emp_ccgid,emp_name,emp_email,emp_position_id,supervisor_emp_id,supervisor_ccgid,supervisor_name,supervisor_position_id,sr_manager_emp_id,sr_manager_ccgid,sr_manager_name,sr_manager_position_id,domain_head_emp_id,domain_head_ccgid,domain_head_name,domain_head_position_id,center,site,gbs_domain,pl1,pl2,pl3_code,pl3,carrier,customer_country,hc,management_or_production,cost_type
                        2026-06,EMP-1,,Agent One,s00000001@dev.local,EMP-POS-1,SUP-1,S00000002,Supervisor One,POS-SUP-1,SRM-1,S00000003,Manager One,POS-SRM-1,DH-1,S00000004,Head One,POS-DH-1,GBS INDIA,Site A,Finance,PL1,PL2,PL3,PL3 Name,CMA,MY,1,production,productive
                        """;
                return java.util.List.of(new Source(
                        "Monthly Report of 202606(GBS INDIA).csv",
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        "drive-monthly-1",
                        "etag-monthly-1",
                        "SHAREPOINT",
                        LocalDate.of(2026, 6, 30)));
            }
        };
        TimesheetSyncAlertNotifier notifier = new TimesheetSyncAlertNotifier(null, null, null, null) {
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
                unusedRepository(OrgPersonRepository.class),
                unusedRepository(OrgPositionRepository.class),
                unusedRepository(OrgPositionParentRepository.class),
                unusedRepository(OrgSeatRepository.class),
                capturingRepository(SupervisorProcessRepository.class, savedScopes),
                capturingRepository(ProcessScopeRepository.class, savedProcesses),
                capturingRepository(SupervisorDeliveryHcRepository.class, savedKpis),
                issues,
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                notifier,
                stubAudits());

        assertThatThrownBy(() -> failing.sync("MONTHLY")).isInstanceOf(ApiException.class);
        assertThat(mails.get()).isEqualTo(1);
        assertThat(runStore.values()).hasSize(1).allMatch(run -> "FAILED".equals(run.getStatus()));
    }

    @Test
    void missingFieldOnMonthlyRstFailsTheRun() {
        AtomicInteger mails = new AtomicInteger();
        List<SourceIssue> savedIssues = new ArrayList<>();
        TimesheetSourceResolver sources = new TimesheetSourceResolver(
                new TimesheetSharePointProperties(null, null, "4.RST/2.UAT"), null) {
            @Override
            public java.util.List<Source> openAll(String kind) {
                String csv =
                        """
                        month,emp_emp_id,emp_ccgid,emp_name,emp_email,emp_position_id,supervisor_emp_id,supervisor_ccgid,supervisor_name,supervisor_position_id,sr_manager_emp_id,sr_manager_ccgid,sr_manager_name,sr_manager_position_id,domain_head_emp_id,domain_head_ccgid,domain_head_name,domain_head_position_id,center,site,gbs_domain,pl1,pl2,pl3_code,pl3,carrier,customer_country,hc,management_or_production,cost_type
                        2026-06,EMP-1,S00000001,Agent One,s00000001@dev.local,EMP-POS-1,SUP-1,S00000002,Supervisor One,POS-SUP-1,SRM-1,S00000003,Manager One,POS-SRM-1,DH-1,S00000004,Head One,POS-DH-1,GBS INDIA,Site A,Finance,PL1,PL2,PL3,PL3 Name,CMA,MY,1,production,productive
                        2026-06,EMP-2,,Agent Two,s00000005@dev.local,EMP-POS-2,SUP-1,S00000002,Supervisor One,POS-SUP-1,SRM-1,S00000003,Manager One,POS-SRM-1,DH-1,S00000004,Head One,POS-DH-1,GBS INDIA,Site B,Finance,PL1,PL2,PL3,PL3 Name,CMA,SG,1,production,productive
                        """;
                return java.util.List.of(new Source(
                        "Monthly Report of 202606(GBS INDIA).csv",
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        "drive-monthly-3",
                        "etag-monthly-3",
                        "SHAREPOINT",
                        LocalDate.of(2026, 6, 30)));
            }
        };
        TimesheetSyncAlertNotifier notifier = new TimesheetSyncAlertNotifier(null, null, null, null) {
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
                proxy(SourceSnapshotRepository.class, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "saveAndFlush", "save" -> store((SourceSnapshot) args[0]);
                            case "findById" -> Optional.ofNullable(runStore.get(args[0]));
                            case "findByKindAndStatus" -> runStore.values().stream()
                                    .filter(run -> run.getKind().equals(args[0]) && run.getStatus().equals(args[1]))
                                    .toList();
                            case "findByKindAndStatusAndCenter" -> runStore.values().stream()
                                    .filter(run -> run.getKind().equals(args[0])
                                            && run.getStatus().equals(args[1])
                                            && run.getCenter().equals(args[2]))
                                    .findFirst();
                            case "findByKindAndStatusAndSourceDriveItemIdAndSourceEtag" -> Optional.empty();
                            case "findMaxAttemptNo" -> null;
                            case "archiveOtherActive" -> archiveOther(
                                    (String) args[0], (String) args[1], (UUID) args[2], (Instant) args[3]);
                            default -> unsupported(method);
                        }),
                unusedRepository(OrgPersonRepository.class),
                unusedRepository(OrgPositionRepository.class),
                unusedRepository(OrgPositionParentRepository.class),
                unusedRepository(OrgSeatRepository.class),
                capturingRepository(SupervisorProcessRepository.class, savedScopes),
                capturingRepository(ProcessScopeRepository.class, savedProcesses),
                capturingRepository(SupervisorDeliveryHcRepository.class, savedKpis),
                proxy(SourceIssueRepository.class, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "saveAll" -> {
                                @SuppressWarnings("unchecked")
                                Iterable<SourceIssue> rows = (Iterable<SourceIssue>) args[0];
                                rows.forEach(savedIssues::add);
                                yield savedIssues;
                            }
                            case "findBySyncRunIdOrderBySourceRowAscCreatedAtAsc" -> savedIssues.stream()
                                    .filter(issue -> issue.getSyncRunId().equals(args[0]))
                                    .toList();
                            default -> unsupported(method);
                        }),
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                notifier,
                stubAudits());

        assertThatThrownBy(() -> tolerant.sync("MONTHLY")).isInstanceOf(ApiException.class);
        assertThat(mails.get()).isEqualTo(1);
        assertThat(runStore.values()).hasSize(1).allMatch(run -> "FAILED".equals(run.getStatus()));
        assertThat(savedIssues).extracting(SourceIssue::getCode).containsExactly("MISSING_FIELD");
        assertThat(savedScopes).isEmpty();
        assertThat(savedKpis).isEmpty();
    }

    private static AuditRecorder stubAudits() {
        AuditRecorder audits = Mockito.mock(AuditRecorder.class);
        Mockito.when(audits.recordSync(Mockito.any())).thenAnswer(invocation -> AuditEvent.record(
                AuditEntityType.TIMESHEET_SYNC,
                invocation.getArgument(0),
                AuditAction.CREATE,
                "SYSTEM",
                "SYSTEM",
                "SYSTEM",
                "SYSTEM",
                null,
                NOW));
        return audits;
    }

    private SourceSnapshot store(SourceSnapshot run) {
        runStore.put(run.getId(), run);
        return run;
    }

    private int archiveOther(String kind, String center, UUID keepRunId, Instant completedAt) {
        int archived = 0;
        for (SourceSnapshot run : runStore.values()) {
            if (kind.equals(run.getKind())
                    && center.equals(run.getCenter())
                    && "ACTIVE".equals(run.getStatus())
                    && !keepRunId.equals(run.getId())) {
                run.markArchived(completedAt);
                archived++;
            }
        }
        return archived;
    }

    private SourceSnapshotRepository syncRunRepository() {
        return proxy(SourceSnapshotRepository.class, (proxy, method, args) ->
                switch (method.getName()) {
                    case "saveAndFlush", "save" -> store((SourceSnapshot) args[0]);
                    case "findById" -> Optional.ofNullable(runStore.get(args[0]));
                    case "findByKindAndStatus" -> runStore.values().stream()
                            .filter(run -> run.getKind().equals(args[0]) && run.getStatus().equals(args[1]))
                            .toList();
                    case "findByKindAndStatusAndCenter" -> runStore.values().stream()
                            .filter(run -> run.getKind().equals(args[0])
                                    && run.getStatus().equals(args[1])
                                    && run.getCenter().equals(args[2]))
                            .findFirst();
                    case "findByKindAndStatusAndSourceDriveItemIdAndSourceEtag" -> Optional.empty();
                    case "findMaxAttemptNo" -> null;
                    case "archiveOtherActive" -> archiveOther(
                            (String) args[0], (String) args[1], (UUID) args[2], (Instant) args[3]);
                    default -> unsupported(method);
                });
    }

    private static Source monthlySource() {
        InputStream content = TimesheetSyncServiceTests.class.getResourceAsStream("/timesheet/monthly-sample.csv");
        assertThat(content).as("monthly-sample.csv").isNotNull();
        return new Source(
                "Monthly Report of 202606(GBS INDIA).csv",
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
            if ("deleteStaleForCenter".equals(method.getName())) {
                String center = (String) args[1];
                UUID keepRunId = (UUID) args[2];
                target.removeIf(row ->
                        !keepRunId.equals(syncRunIdOf(row)) && center.equals(centerOf(row)));
                return target.size();
            }
            return unsupported(method);
        });
    }

    private static UUID syncRunIdOf(Object row) {
        return switch (row) {
            case SupervisorProcess scope -> scope.getSyncRunId();
            case ProcessScope process -> process.getSyncRunId();
            case SupervisorDeliveryHc kpi -> kpi.getSyncRunId();
            default -> throw new AssertionError("Unexpected computed row: " + row);
        };
    }

    private static String centerOf(Object row) {
        return switch (row) {
            case SupervisorProcess scope -> scope.getCenter();
            case ProcessScope process -> process.getCenter();
            case SupervisorDeliveryHc kpi -> kpi.getCenter();
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
