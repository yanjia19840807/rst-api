package com.cmacgm.gbs.rst.api.graph;

import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphCollection;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphDrive;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphDriveItem;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphSite;

/**
 * Microsoft Graph client-credentials access to the Timesheet SharePoint library.
 */
@Service
public class MicrosoftGraphService {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftGraphService.class);
    private static final ParameterizedTypeReference<GraphCollection<GraphDrive>> DRIVES =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<GraphCollection<GraphDriveItem>> DRIVE_ITEMS =
            new ParameterizedTypeReference<>() {
            };

    private final MicrosoftGraphProperties properties;
    private final MicrosoftGraphClient client;

    private volatile String siteId;
    private volatile String driveId;

    /**
     * @param properties Graph client-credentials settings
     * @param client Graph HTTP client
     */
    public MicrosoftGraphService(MicrosoftGraphProperties properties, MicrosoftGraphClient client) {
        this.properties = properties;
        this.client = client;
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
        String encodedPath = MicrosoftGraphPaths.encodeDrivePath(driveItemPath);
        String path = "/drives/" + driveId() + "/root:/" + encodedPath;
        log.info("Microsoft Graph getDriveItem {}", path);
        return client.get(client.graphUri(path), GraphDriveItem.class);
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
        return client.get(
                client.graphUri("/drives/" + driveId() + "/items/" + driveItemId + "/children"),
                DRIVE_ITEMS)
                .items();
    }

    /**
     * Downloads file content by drive item id.
     *
     * @param driveItemId file item id
     * @return content stream
     */
    public InputStream getDriveItemContentById(String driveItemId) {
        requireItemId(driveItemId);
        return client.getContent(client.graphUri("/drives/" + driveId() + "/items/" + driveItemId + "/content"));
    }

    /**
     * Loads drive item metadata by id.
     *
     * @param driveItemId item id
     * @return drive item
     */
    public GraphDriveItem getDriveItemById(String driveItemId) {
        requireItemId(driveItemId);
        return client.get(
                client.graphUri("/drives/" + driveId() + "/items/" + driveItemId),
                GraphDriveItem.class);
    }

    private String siteId() {
        String existing = siteId;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (siteId == null) {
                String graphSiteId = MicrosoftGraphPaths.siteIdFromWebUrl(properties.sharepointSite());
                GraphSite site = client.get(client.graphUri("/sites/" + graphSiteId), GraphSite.class);
                if (site.id() == null || site.id().isBlank()) {
                    throw new ApiException(
                            HttpStatus.BAD_GATEWAY,
                            "graph-site-missing",
                            "SharePoint site was not found: " + properties.sharepointSite());
                }
                siteId = site.id();
            }
            return siteId;
        }
    }

    private String driveId() {
        String existing = driveId;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (driveId == null) {
                driveId = resolveTimesheetDriveId();
            }
            return driveId;
        }
    }

    private String resolveTimesheetDriveId() {
        GraphCollection<GraphDrive> response = client.get(
                client.graphUri("/sites/" + siteId() + "/drives"),
                DRIVES);
        for (GraphDrive drive : response.items()) {
            if (properties.listName().equals(drive.name()) && drive.id() != null) {
                return drive.id();
            }
        }
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "graph-drive-missing",
                "SharePoint library was not found: " + properties.listName());
    }

    private static void requireItemId(String driveItemId) {
        if (driveItemId == null || driveItemId.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "graph-item-id-required",
                    "SharePoint drive item id is required.");
        }
    }
}
