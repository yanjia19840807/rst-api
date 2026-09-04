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
import org.springframework.http.MediaType;

import com.cmacgm.gbs.rst.api.exercise.associateddata.application.HolidayExcelService;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.ImportTemplateService;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.SupportExcelService;
import com.cmacgm.gbs.rst.api.exercise.associateddata.application.VolumeExcelService;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphDriveItem;

/**
 * Live Graph write into the UAT RST output folders. Skips when tenant/secret are absent.
 *
 * <p>SharePoint:
 * {@code /sites/CMA-SharedKPIAutomation/Timesheet/2.UAT/Data Output/RST}
 */
class MicrosoftGraphRstFolderIT {

    static final String DAILY_FOLDER = "2.UAT/Data Output/RST/Daily";
    static final String MONTHLY_FOLDER = "2.UAT/Data Output/RST/Monthly";
    static final String TEMPLATE_FOLDER = "2.UAT/Data Output/RST/Template";
    static final String DAILY_FILE = "Daily Report of 20260727(GBS CHINA).xlsx";
    static final String MONTHLY_FILE = "Monthly Report of 202606(GBS CHINA).xlsx";
    static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

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
    void uploadsTimesheetReportsToUatFolders() throws Exception {
        Path daily = localReport(DAILY_FILE);
        Path monthly = localReport(MONTHLY_FILE);
        assumeTrue(daily != null, "Daily workbook not found under rst-material/Timesheet");
        assumeTrue(monthly != null, "Monthly workbook not found under rst-material/Timesheet");

        MicrosoftGraphService graph = liveGraph();
        assumeTrue(graph != null, "Microsoft Graph credentials are incomplete.");

        GraphDriveItem dailyFolder = graph.getDriveItem(DAILY_FOLDER);
        assertThat(dailyFolder.isFolder()).as("Daily path must be a folder").isTrue();
        GraphDriveItem monthlyFolder = graph.getDriveItem(MONTHLY_FOLDER);
        assertThat(monthlyFolder.isFolder()).as("Monthly path must be a folder").isTrue();

        byte[] dailyBytes = Files.readAllBytes(daily);
        GraphDriveItem uploadedDaily = graph.putDriveItemContent(
                DAILY_FOLDER, DAILY_FILE, dailyBytes, XLSX);
        assertThat(uploadedDaily.name()).isEqualTo(DAILY_FILE);
        assertThat(uploadedDaily.isFile()).isTrue();
        assertThat(uploadedDaily.size()).isPositive();

        byte[] monthlyBytes = Files.readAllBytes(monthly);
        GraphDriveItem uploadedMonthly = graph.putDriveItemContent(
                MONTHLY_FOLDER, MONTHLY_FILE, monthlyBytes, XLSX);
        assertThat(uploadedMonthly.name()).isEqualTo(MONTHLY_FILE);
        assertThat(uploadedMonthly.isFile()).isTrue();
        assertThat(uploadedMonthly.size()).isPositive();

        assertThat(graph.getChildrenByFolderPath(DAILY_FOLDER))
                .extracting(GraphDriveItem::name)
                .contains(DAILY_FILE);
        assertThat(graph.getChildrenByFolderPath(MONTHLY_FOLDER))
                .extracting(GraphDriveItem::name)
                .contains(MONTHLY_FILE);
    }

    @Test
    void createsTemplateFolderAndUploadsImportTemplates() {
        MicrosoftGraphService graph = liveGraph();
        assumeTrue(graph != null, "Microsoft Graph credentials are incomplete.");

        GraphDriveItem folder = graph.ensureFolder(TEMPLATE_FOLDER);
        assertThat(folder.isFolder()).as("Template path must be a folder").isTrue();

        HolidayExcelService holidays = new HolidayExcelService();
        VolumeExcelService volumes = new VolumeExcelService();
        SupportExcelService support = new SupportExcelService();
        for (ImportTemplateService.Kind kind : ImportTemplateService.Kind.values()) {
            byte[] body = switch (kind) {
                case CALENDAR -> holidays.exportBlank();
                case VOLUME_MONTHLY -> volumes.exportMonthlyBlank();
                case VOLUME_DAILY -> volumes.exportDailyBlank();
                case VOLUME_SLOT -> volumes.exportSlotBlank();
                case SUPPORT -> support.exportBlank();
            };
            GraphDriveItem uploaded = graph.putDriveItemContent(
                    TEMPLATE_FOLDER, kind.fileName(), body, XLSX);
            assertThat(uploaded.name()).isEqualTo(kind.fileName());
            assertThat(uploaded.isFile()).isTrue();
        }

        assertThat(graph.getChildrenByFolderPath(TEMPLATE_FOLDER))
                .extracting(GraphDriveItem::name)
                .contains(
                        ImportTemplateService.Kind.CALENDAR.fileName(),
                        ImportTemplateService.Kind.VOLUME_MONTHLY.fileName(),
                        ImportTemplateService.Kind.VOLUME_DAILY.fileName(),
                        ImportTemplateService.Kind.VOLUME_SLOT.fileName(),
                        ImportTemplateService.Kind.SUPPORT.fileName());
    }

    private static MicrosoftGraphService liveGraph() {
        assumeTrue(hasText(env("AZURE_TENANT_ID")) || hasText(env("MS_GRAPH_TENANT_ID")),
                "Set AZURE_TENANT_ID in rst-api/.env");
        assumeTrue(hasText(env("MS_GRAPH_CLIENT_SECRET")), "Set MS_GRAPH_CLIENT_SECRET in rst-api/.env");
        MicrosoftGraphProperties properties = new MicrosoftGraphProperties(
                true,
                envOr("MS_GRAPH_SECRET_NAME", "timesheet-prd-microsoft-graph-credentials"),
                envOr("MS_GRAPH_TENANT_ID", env("AZURE_TENANT_ID")),
                envOr("MS_GRAPH_CLIENT_ID", "5282c4c7-7d9a-4ddc-b8bb-955921a0adf7"),
                env("MS_GRAPH_CLIENT_SECRET"),
                envOr("MS_GRAPH_SHAREPOINT_SITE", "https://cmacgmgroup.sharepoint.com/sites/CMA-SharedKPIAutomation"),
                envOr("MS_GRAPH_LIST_NAME", "Timesheet"),
                envOr("MS_GRAPH_FROM_MAIL", "GBS.TIMESHEET@cma-cgm.com"));
        if (!properties.hasCredentials()) {
            return null;
        }
        return new MicrosoftGraphService(properties);
    }

    private static Path localReport(String fileName) {
        for (Path candidate : List.of(
                Path.of("../rst-material/Timesheet").resolve(fileName),
                Path.of("rst-material/Timesheet").resolve(fileName),
                Path.of("/Users/yanjia/Code/cma-cgm/RST/Projects/rst-material/Timesheet")
                        .resolve(fileName))) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
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
