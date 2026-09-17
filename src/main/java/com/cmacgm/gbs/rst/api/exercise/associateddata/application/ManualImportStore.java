package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.FileArtifact;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphDriveItem;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphProperties;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetSharePointProperties;

/**
 * Stores Exercise import attachments under {@code {root}/Manual/{module}}.
 */
@Component
public class ManualImportStore {

    public enum Module {
        VOLUME("Volume"),
        CALENDAR("Calendar"),
        SUPPORT("Support"),
        CYCLE_TIME("CycleTime");

        private final String folderName;

        Module(String folderName) {
            this.folderName = folderName;
        }

        public String folderName() {
            return folderName;
        }
    }

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final String DEFAULT_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TimesheetSharePointProperties sharePoint;
    private final MicrosoftGraphProperties graphProperties;
    private final MicrosoftGraphService graph;

    public ManualImportStore(
            TimesheetSharePointProperties sharePoint,
            MicrosoftGraphProperties graphProperties,
            MicrosoftGraphService graph) {
        this.sharePoint = sharePoint;
        this.graphProperties = graphProperties;
        this.graph = graph;
    }

    /**
     * Uploads to the environment Manual module folder when Graph is configured; otherwise a stub.
     */
    public FileArtifact store(
            Module module,
            String artifactType,
            String businessObjectType,
            UUID businessObjectId,
            String originalFileName,
            String mimeType,
            byte[] content,
            String actorCcgid,
            Instant now) {
        String displayName = blankToDefault(originalFileName, "import.xlsx");
        String type = mimeType == null || mimeType.isBlank() ? DEFAULT_XLSX : mimeType;
        long size = content == null ? 0L : content.length;
        if (!graphProperties.hasCredentials()) {
            return FileArtifact.createStub(
                    artifactType,
                    businessObjectType,
                    businessObjectId,
                    displayName,
                    type,
                    size,
                    actorCcgid,
                    now);
        }
        String storedName = storedFileName(now, artifactType, businessObjectId, displayName);
        String folder = sharePoint.manualModuleFolder(module.folderName());
        graph.ensureFolder(folder);
        GraphDriveItem item = graph.putDriveItemContent(
                folder, storedName, content == null ? new byte[0] : content, MediaType.parseMediaType(type));
        String webUrl = item.webUrl();
        if (webUrl == null || webUrl.isBlank()) {
            webUrl = sharePoint.site() + "/" + sharePoint.library() + "/" + folder + "/" + storedName;
        }
        return FileArtifact.createStored(
                artifactType,
                businessObjectType,
                businessObjectId,
                item.id(),
                webUrl,
                item.name() == null || item.name().isBlank() ? storedName : item.name(),
                type,
                item.size() == null ? size : item.size(),
                actorCcgid,
                now);
    }

    static String storedFileName(Instant now, String artifactType, UUID exerciseId, String originalFileName) {
        String stamp = FILE_STAMP.format(now == null ? Instant.EPOCH : now);
        String kind = sanitizeToken(artifactType, "IMPORT");
        String exercise = exerciseId == null ? "unknown" : exerciseId.toString().replace("-", "").substring(0, 8);
        return stamp + "_" + kind + "_" + exercise + "_" + sanitizeFileName(originalFileName);
    }

    static String sanitizeFileName(String fileName) {
        String trimmed = fileName == null ? "" : fileName.replace('\\', '/').strip();
        int slash = trimmed.lastIndexOf('/');
        if (slash >= 0) {
            trimmed = trimmed.substring(slash + 1);
        }
        String cleaned = trimmed.replaceAll("[\\\\/:*?\"<>|#%]", "_").strip();
        if (cleaned.isBlank()) {
            return "import.xlsx";
        }
        if (cleaned.length() <= 120) {
            return cleaned;
        }
        int dot = cleaned.lastIndexOf('.');
        String ext = dot > 0 && cleaned.length() - dot <= 12 ? cleaned.substring(dot) : "";
        String stem = ext.isEmpty() ? cleaned : cleaned.substring(0, dot);
        int maxStem = Math.max(1, 120 - ext.length());
        return stem.substring(0, Math.min(stem.length(), maxStem)) + ext;
    }

    private static String sanitizeToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9_-]", "_");
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
