package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphProperties;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncRun;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Live SharePoint sync into local Postgres. Break on
 * {@link TimesheetSyncService#sync(String)}.
 *
 * <p>Uses the {@code dev} profile: {@code rst-api/.env} datasource and p6spy.
 * Full SQL is printed to stdout so it shows in Debug Console. This writes and
 * may archive the current ACTIVE Daily / Monthly snapshot.
 *
 * <p>Skipped when {@code MS_GRAPH_CLIENT_SECRET} is absent.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIf("liveEnabled")
@TestPropertySource(properties = {
        "mail.enabled=false",
        "forecast.enabled=false",
        "timesheet.sync.daily.enabled=false",
        "timesheet.sync.monthly.enabled=false",
        "decorator.datasource.enabled=true",
        "decorator.datasource.p6spy.enable-logging=true",
        "decorator.datasource.p6spy.logging=sysout",
        "decorator.datasource.p6spy.multiline=true",
        "decorator.datasource.p6spy.log-format=%(executionTime)ms | %(category) | connection %(connectionId)%n%(sql)",
        "logging.level.p6spy=INFO",
        "logging.level.org.hibernate.SQL=DEBUG",
        "logging.level.org.hibernate.orm.jdbc.bind=TRACE"
})
class TimesheetSyncLiveIT {

    private static final Map<String, String> DOT_ENV = new HashMap<>();

    @Autowired
    private TimesheetSyncService sync;

    @Autowired
    private TimesheetSyncRunRepository syncRuns;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MicrosoftGraphProperties graph;

    @BeforeAll
    static void loadLocalEnv() {
        readDotEnv();
    }

    static boolean liveEnabled() {
        readDotEnv();
        return hasText(env("MS_GRAPH_CLIENT_SECRET"))
                && (hasText(env("AZURE_TENANT_ID")) || hasText(env("MS_GRAPH_TENANT_ID")));
    }

    @BeforeEach
    void requireGraph() {
        assumeTrue(graph.hasCredentials(), "Microsoft Graph credentials are incomplete.");
    }

    @Test
    void syncsDailyFromSharePoint() {
        TimesheetSyncService.SyncResult result = sync.sync("DAILY").getFirst();

        assertThat(result.kind()).isEqualTo("DAILY");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.syncDate()).isNotNull();
        TimesheetSyncRun run = syncRuns.findById(result.id()).orElseThrow();
        assertThat(run.getSourceType()).isEqualTo("SHAREPOINT");
        assertThat(run.getSourceFileName()).isNotBlank();
        assertThat(count("timesheet_person", result.id())).isPositive();
        assertThat(count("timesheet_position", result.id())).isPositive();
        assertThat(count("timesheet_person_position_role", result.id())).isPositive();
    }

    @Test
    void syncsMonthlyFromSharePoint() {
        TimesheetSyncService.SyncResult result = sync.sync("MONTHLY").getFirst();

        assertThat(result.kind()).isEqualTo("MONTHLY");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.syncDate()).isNotNull();
        TimesheetSyncRun run = syncRuns.findById(result.id()).orElseThrow();
        assertThat(run.getSourceType()).isEqualTo("SHAREPOINT");
        assertThat(run.getSourceFileName()).isNotBlank();
        assertThat(count("timesheet_scope", result.id())).isPositive();
        assertThat(count("timesheet_kpi", result.id())).isPositive();
    }

    private long count(String table, UUID syncRunId) {
        Long rows = jdbc.queryForObject(
                "select count(*) from " + table + " where sync_run_id = ?",
                Long.class,
                syncRunId);
        return rows == null ? 0L : rows;
    }

    private static void readDotEnv() {
        if (!DOT_ENV.isEmpty()) {
            return;
        }
        for (Path candidate : List.of(Path.of(".env"), Path.of("rst-api/.env"))) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(candidate, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }
                    int split = trimmed.indexOf('=');
                    String key = trimmed.substring(0, split).trim();
                    String value = trimmed.substring(split + 1).trim();
                    if ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    DOT_ENV.putIfAbsent(key, value);
                }
            } catch (Exception ignored) {
                return;
            }
        }
    }

    private static String env(String key) {
        String fromProcess = System.getenv(key);
        if (hasText(fromProcess)) {
            return fromProcess;
        }
        String fromDotEnv = DOT_ENV.get(key);
        return fromDotEnv == null ? "" : fromDotEnv;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
