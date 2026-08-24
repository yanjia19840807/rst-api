package com.cmacgm.gbs.rst.api.graph;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphDriveItem;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphFile;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphFolder;
import com.microsoft.graph.models.Drive;
import com.microsoft.graph.models.DriveCollectionResponse;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemCollectionResponse;
import com.microsoft.graph.models.Folder;
import com.microsoft.graph.models.Site;
import com.microsoft.graph.models.odataerrors.ODataError;
import com.microsoft.graph.serviceclient.GraphServiceClient;

/**
 * Microsoft Graph access to the Timesheet SharePoint library via the official SDK.
 */
@Service
public class MicrosoftGraphService {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftGraphService.class);
    private static final String[] SCOPES = {"https://graph.microsoft.com/.default"};

    private final MicrosoftGraphProperties properties;
    private final Object lock = new Object();

    private volatile GraphServiceClient graph;
    private volatile String siteId;
    private volatile String driveId;

    /**
     * @param properties Graph client-credentials settings
     */
    public MicrosoftGraphService(MicrosoftGraphProperties properties) {
        this.properties = properties;
    }

    /**
     * @return configured Graph properties
     */
    public MicrosoftGraphProperties properties() {
        return properties;
    }

    /**
     * Resolves a drive item by folder path under the Timesheet library.
     *
     * @param driveItemPath path relative to the library root, for example
     *        {@code 3.Production/Data Input/MyHR}
     * @return drive item
     */
    public GraphDriveItem getDriveItem(String driveItemPath) {
        String itemId = rootItemId(driveItemPath);
        log.info("Microsoft Graph getDriveItem {}", itemId);
        return toView(invoke("get drive item " + driveItemPath, () ->
                graph().drives().byDriveId(driveId()).items().byDriveItemId(itemId).get()));
    }

    /**
     * Lists immediate children of a folder path.
     *
     * @param driveItemPath folder path relative to the library root
     * @return child drive items, never null
     */
    public List<GraphDriveItem> getChildrenByFolderPath(String driveItemPath) {
        GraphDriveItem item = getDriveItem(driveItemPath);
        if (!item.isFolder()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "graph-not-folder",
                    "SharePoint path is not a folder: " + driveItemPath);
        }
        return getChildrenByFolderId(item.id());
    }

    /**
     * Lists immediate children of a folder drive item.
     *
     * @param driveItemId folder item id
     * @return child drive items, never null
     */
    public List<GraphDriveItem> getChildrenByFolderId(String driveItemId) {
        requireItemId(driveItemId);
        DriveItemCollectionResponse response = invoke("list children", () ->
                graph().drives().byDriveId(driveId()).items().byDriveItemId(driveItemId).children().get());
        List<DriveItem> value = response.getValue();
        if (value == null) {
            return List.of();
        }
        List<GraphDriveItem> items = new ArrayList<>(value.size());
        for (DriveItem item : value) {
            items.add(toView(item));
        }
        return items;
    }

    /**
     * Downloads file content by drive item id.
     *
     * @param driveItemId file item id
     * @return content stream
     */
    public InputStream getDriveItemContentById(String driveItemId) {
        requireItemId(driveItemId);
        return invoke("download content", () ->
                graph().drives().byDriveId(driveId()).items().byDriveItemId(driveItemId).content().get());
    }

    /**
     * Creates or replaces a file under a folder path in the Timesheet library.
     *
     * @param folderPath folder relative to the library root, for example
     *        {@code 2.UAT/Data Output/RST}
     * @param fileName file name
     * @param content file bytes
     * @param contentType unused; Graph simple upload is octet-stream
     * @return created or updated drive item
     */
    public GraphDriveItem putDriveItemContent(
            String folderPath, String fileName, byte[] content, MediaType contentType) {
        if (fileName == null || fileName.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "graph-file-name-required", "SharePoint file name is required.");
        }
        String itemPath = MicrosoftGraphPaths.folderPath(folderPath, fileName);
        String itemId = rootItemId(itemPath);
        log.info("Microsoft Graph putDriveItemContent {}", itemId);
        byte[] body = content == null ? new byte[0] : content;
        invoke("upload " + itemPath, () -> {
            graph().drives()
                    .byDriveId(driveId())
                    .items()
                    .byDriveItemId(itemId)
                    .content()
                    .put(new ByteArrayInputStream(body));
            return Boolean.TRUE;
        });
        return getDriveItem(itemPath);
    }

    /**
     * Returns a drive item when it exists.
     *
     * @param driveItemPath path relative to the library root
     * @return item, or empty when Graph returns 404
     */
    public Optional<GraphDriveItem> findDriveItem(String driveItemPath) {
        String itemId = rootItemId(driveItemPath);
        try {
            DriveItem item = graph().drives().byDriveId(driveId()).items().byDriveItemId(itemId).get();
            return item == null ? Optional.empty() : Optional.of(toView(item));
        } catch (ApiException ex) {
            throw ex;
        } catch (ODataError ex) {
            if (statusOf(ex) == 404) {
                return Optional.empty();
            }
            throw graphFailure("get drive item " + driveItemPath, statusOf(ex), messageOf(ex));
        } catch (com.microsoft.kiota.ApiException ex) {
            if (statusOf(ex) == 404) {
                return Optional.empty();
            }
            throw graphFailure("get drive item " + driveItemPath, statusOf(ex), ex.getMessage());
        } catch (RuntimeException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "graph-unreachable",
                    "Microsoft Graph is unreachable: " + ex.getMessage());
        }
    }

    /**
     * Creates the folder and any missing parents.
     *
     * @param folderPath folder relative to the library root
     * @return folder item
     */
    public GraphDriveItem ensureFolder(String folderPath) {
        return findDriveItem(folderPath).orElseGet(() -> createFolder(folderPath));
    }

    /**
     * Downloads file bytes by folder + file name.
     *
     * @param folderPath folder relative to the library root
     * @param fileName file name
     * @return file bytes
     */
    public byte[] getFileBytes(String folderPath, String fileName) {
        String itemPath = MicrosoftGraphPaths.folderPath(folderPath, fileName);
        GraphDriveItem item = getDriveItem(itemPath);
        try (InputStream content = getDriveItemContentById(item.id())) {
            return content.readAllBytes();
        } catch (IOException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "graph-download-failed",
                    "Unable to read SharePoint file " + itemPath + ": " + ex.getMessage());
        }
    }

    /**
     * Loads drive item metadata by id.
     *
     * @param driveItemId item id
     * @return drive item
     */
    public GraphDriveItem getDriveItemById(String driveItemId) {
        requireItemId(driveItemId);
        return toView(invoke("get drive item by id", () ->
                graph().drives().byDriveId(driveId()).items().byDriveItemId(driveItemId).get()));
    }

    private GraphServiceClient graph() {
        GraphServiceClient existing = graph;
        if (existing != null) {
            return existing;
        }
        synchronized (lock) {
            if (graph == null) {
                ensureConfigured();
                graph = new GraphServiceClient(
                        new ClientSecretCredentialBuilder()
                                .tenantId(properties.tenantId())
                                .clientId(properties.clientId())
                                .clientSecret(properties.clientSecret())
                                .build(),
                        SCOPES);
                log.info(
                        "Microsoft Graph client created: secretName={} clientId={}",
                        properties.secretName(),
                        properties.clientId());
            }
            return graph;
        }
    }

    private String siteId() {
        String existing = siteId;
        if (existing != null) {
            return existing;
        }
        synchronized (lock) {
            if (siteId == null) {
                String graphSiteId = MicrosoftGraphPaths.siteIdFromWebUrl(properties.sharepointSite());
                Site site = invoke("resolve site", () -> graph().sites().bySiteId(graphSiteId).get());
                if (site.getId() == null || site.getId().isBlank()) {
                    throw new ApiException(
                            HttpStatus.BAD_GATEWAY,
                            "graph-site-missing",
                            "SharePoint site was not found: " + properties.sharepointSite());
                }
                siteId = site.getId();
            }
            return siteId;
        }
    }

    private String driveId() {
        String existing = driveId;
        if (existing != null) {
            return existing;
        }
        synchronized (lock) {
            if (driveId == null) {
                driveId = resolveTimesheetDriveId();
            }
            return driveId;
        }
    }

    private String resolveTimesheetDriveId() {
        DriveCollectionResponse response = invoke("list drives", () ->
                graph().sites().bySiteId(siteId()).drives().get());
        List<Drive> drives = response.getValue();
        if (drives != null) {
            for (Drive drive : drives) {
                if (properties.listName().equals(drive.getName()) && drive.getId() != null) {
                    return drive.getId();
                }
            }
        }
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "graph-drive-missing",
                "SharePoint library was not found: " + properties.listName());
    }

    private void ensureConfigured() {
        if (!properties.enabled()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "graph-disabled",
                    "Microsoft Graph is disabled. Set MS_GRAPH_ENABLED=true to use SharePoint.");
        }
        if (!properties.hasCredentials()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "graph-not-configured",
                    "Microsoft Graph credentials are missing. Set AZURE_TENANT_ID, "
                            + "MS_GRAPH_CLIENT_ID and MS_GRAPH_CLIENT_SECRET "
                            + "(secret name " + properties.secretName() + ").");
        }
    }

    private <T> T invoke(String action, GraphCall<T> call) {
        try {
            T result = call.run();
            if (result == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "graph-empty-response",
                        "Microsoft Graph returned an empty response for " + action);
            }
            return result;
        } catch (ApiException ex) {
            throw ex;
        } catch (ODataError ex) {
            throw graphFailure(action, statusOf(ex), messageOf(ex));
        } catch (com.microsoft.kiota.ApiException ex) {
            throw graphFailure(action, statusOf(ex), ex.getMessage());
        } catch (RuntimeException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "graph-unreachable",
                    "Microsoft Graph is unreachable: " + ex.getMessage());
        }
    }

    private static ApiException graphFailure(String action, int status, String detail) {
        String suffix = detail == null || detail.isBlank() ? "" : ": " + truncate(detail);
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "graph-request-failed",
                "Graph request failed for " + action + " (HTTP " + status + ")" + suffix);
    }

    private static int statusOf(com.microsoft.kiota.ApiException ex) {
        int status = ex.getResponseStatusCode();
        return status > 0 ? status : 502;
    }

    private static String messageOf(ODataError error) {
        if (error.getError() != null && error.getError().getMessage() != null) {
            return error.getError().getMessage();
        }
        return error.getMessage();
    }

    private static String truncate(String body) {
        String trimmed = body.trim().replace('\n', ' ');
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 240) + "…";
    }

    private GraphDriveItem createFolder(String folderPath) {
        String path = folderPath == null ? "" : folderPath.strip();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "graph-path-required", "SharePoint folder path is required.");
        }
        int slash = path.lastIndexOf('/');
        String parentPath = slash < 0 ? "" : path.substring(0, slash);
        String name = slash < 0 ? path : path.substring(slash + 1);
        String parentId = parentPath.isBlank() ? "root" : ensureFolder(parentPath).id();
        DriveItem folder = new DriveItem();
        folder.setName(name);
        folder.setFolder(new Folder());
        log.info("Microsoft Graph createFolder {}", path);
        return toView(invoke("create folder " + path, () ->
                graph().drives().byDriveId(driveId()).items().byDriveItemId(parentId).children().post(folder)));
    }

    private static String rootItemId(String relativePath) {
        String path = relativePath == null ? "" : relativePath.strip();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "graph-path-required", "SharePoint drive item path is required.");
        }
        return "root:/" + path + ":";
    }

    private static GraphDriveItem toView(DriveItem item) {
        OffsetDateTime modified = item.getLastModifiedDateTime();
        Integer childCount = item.getFolder() == null ? null : item.getFolder().getChildCount();
        return new GraphDriveItem(
                item.getId(),
                item.getName(),
                item.getWebUrl(),
                item.getSize(),
                item.getFolder() == null ? null : new GraphFolder(childCount),
                item.getFile() == null ? null : new GraphFile(item.getFile().getMimeType()),
                modified,
                item.getETag(),
                null);
    }

    private static void requireItemId(String driveItemId) {
        if (driveItemId == null || driveItemId.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "graph-item-id-required",
                    "SharePoint drive item id is required.");
        }
    }

    @FunctionalInterface
    private interface GraphCall<T> {
        T run();
    }
}
