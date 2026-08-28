package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
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

import com.cmacgm.gbs.rst.api.graph.RstSharePointProperties;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSourceResolver.Source;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetAssignment;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetAssignmentRepository;
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
    private final List<TimesheetAssignment> savedAssignments = new ArrayList<>();
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
                    default -> unsupported(method);
                });
        TimesheetScopeRepository scopes = capturingRepository(TimesheetScopeRepository.class, savedScopes);
        TimesheetAssignmentRepository assignments =
                capturingRepository(TimesheetAssignmentRepository.class, savedAssignments);
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
                syncRuns,
                people,
                positions,
                scopes,
                assignments,
                kpis,
                issues,
                noOpTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC));
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
        assertThat(savedAssignments)
                .extracting(TimesheetAssignment::getEmpCcgid, TimesheetAssignment::getPl3Code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("S00000001", "PL3"),
                        org.assertj.core.groups.Tuple.tuple("S00000005", "PL3"));
        assertThat(savedKpis)
                .extracting(TimesheetKpi::getSite, TimesheetKpi::getCustomerCountry, TimesheetKpi::getHc)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Site A", "MY", new BigDecimal("2.000000")),
                        org.assertj.core.groups.Tuple.tuple("Site B", "SG", new BigDecimal("1.000000")));
        assertThat(savedScopes).allMatch(scope -> scope.getSyncRunId().equals(result.id()));
        assertThat(savedAssignments).allMatch(assignment -> assignment.getSyncRunId().equals(result.id()));
        assertThat(savedKpis).allMatch(kpi -> kpi.getSyncRunId().equals(result.id()));
    }

    private TimesheetSyncRun store(TimesheetSyncRun run) {
        runStore.put(run.getId(), run);
        return run;
    }

    private static Source monthlySource() {
        InputStream content = TimesheetSyncServiceTests.class.getResourceAsStream("/timesheet/monthly-sample.csv");
        assertThat(content).as("monthly-sample.csv").isNotNull();
        return new Source("monthly-sample.csv", content, "drive-monthly-1", "etag-monthly-1");
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
            return unsupported(method);
        });
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
