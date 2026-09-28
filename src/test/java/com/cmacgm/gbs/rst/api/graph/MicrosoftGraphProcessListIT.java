package com.cmacgm.gbs.rst.api.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.mail.application.MailProperties;
import com.cmacgm.gbs.rst.api.process.ProcessProperties;
import com.cmacgm.gbs.rst.api.timesheet.application.GbsProcessCatalog;
import com.cmacgm.gbs.rst.api.timesheet.application.GbsProcessCatalogSource;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetSharePointProperties;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Live Graph read of the GBS Process SharePoint list. Skips when credentials are absent.
 */
class MicrosoftGraphProcessListIT {

    private static final Map<String, String> DOT_ENV = new HashMap<>();

    @BeforeAll
    static void loadLocalEnv() throws Exception {
        for (Path candidate : List.of(Path.of(".env"), Path.of("rst-api/.env"))) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
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
        }
    }

    @Test
    void readsGbsProcessListAndBuildsCatalog() {
        MicrosoftGraphService graph = liveGraph();
        assumeTrue(graph != null, "Microsoft Graph credentials are incomplete.");

        ProcessProperties properties = liveProcessProperties();
        List<Map<String, Object>> rows;
        try {
            rows = graph.getListItemFields(
                    properties.getSharepoint().getSite(),
                    properties.getSharepoint().getList());
        } catch (com.cmacgm.gbs.rst.api.common.error.ApiException ex) {
            assumeTrue(
                    !ex.getMessage().contains("HTTP 403"),
                    "App lacks SharePoint access to "
                            + properties.getSharepoint().getSite()
                            + " — grant Sites.Selected (or Sites.Read.All) for client "
                            + envOr("MS_GRAPH_CLIENT_ID", "2b5ec0be-1344-4161-80f5-b7674d74f019")
                            + ". Detail: "
                            + ex.getMessage());
            throw ex;
        }

        assertThat(rows).isNotEmpty();
        Map<String, Object> sample = rows.getFirst();
        assertThat(sample.keySet().stream().anyMatch(key ->
                        key != null && key.toLowerCase().contains("applicability")))
                .as("expected an RST Applicability field among %s", sample.keySet())
                .isTrue();
        assertThat(sample.containsKey("id") || sample.containsKey("ID"))
                .as("expected id/ID among %s", sample.keySet())
                .isTrue();

        GbsProcessCatalog catalog = new GbsProcessCatalogSource(
                properties, new DefaultResourceLoader(), graph).load();

        assertThat(catalog.rstYesPl3Codes()).isNotEmpty();
        assertThat(catalog.applies("497")).isTrue();
    }

    private static MicrosoftGraphService liveGraph() {
        assumeTrue(hasText(env("AZURE_TENANT_ID")) || hasText(env("MS_GRAPH_TENANT_ID")),
                "Set AZURE_TENANT_ID in rst-api/.env");
        assumeTrue(hasText(env("MS_GRAPH_CLIENT_SECRET")), "Set MS_GRAPH_CLIENT_SECRET in rst-api/.env");
        MicrosoftGraphProperties properties = new MicrosoftGraphProperties(
                envOr("MS_GRAPH_SECRET_NAME", "timesheet-prd-microsoft-graph-credentials"),
                envOr("MS_GRAPH_TENANT_ID", env("AZURE_TENANT_ID")),
                envOr("MS_GRAPH_CLIENT_ID", "2b5ec0be-1344-4161-80f5-b7674d74f019"),
                env("MS_GRAPH_CLIENT_SECRET"));
        if (!properties.hasCredentials()) {
            return null;
        }
        TimesheetSharePointProperties sharePoint = new TimesheetSharePointProperties(
                envOr("TIMESHEET_SHAREPOINT_SITE", "https://cmacgmgroup.sharepoint.com/sites/CMA-SharedKPIAutomation"),
                envOr("TIMESHEET_SHAREPOINT_LIBRARY", "Timesheet"),
                envOr("TIMESHEET_SHAREPOINT_ROOT", "4.RST/2.UAT"));
        return new MicrosoftGraphService(
                properties,
                sharePoint,
                new MailProperties(false, null, envOr("MAIL_FROM", "GBS.TIMESHEET@cma-cgm.com")));
    }

    private static ProcessProperties liveProcessProperties() {
        ProcessProperties properties = new ProcessProperties();
        properties.setRemote(true);
        properties.getSharepoint().setSite(envOr(
                "PROCESS_SHAREPOINT_SITE",
                "https://cmacgmgroup.sharepoint.com/sites/CMA-GlobalBusinessServices"));
        properties.getSharepoint().setList(envOr("PROCESS_SHAREPOINT_LIST", "GBS Process"));
        return properties;
    }

    private static String env(String key) {
        String fromProcess = System.getenv(key);
        if (hasText(fromProcess)) {
            return fromProcess;
        }
        String fromDotEnv = DOT_ENV.get(key);
        return fromDotEnv == null ? "" : fromDotEnv;
    }

    private static String envOr(String key, String fallback) {
        String value = env(key);
        return hasText(value) ? value : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
