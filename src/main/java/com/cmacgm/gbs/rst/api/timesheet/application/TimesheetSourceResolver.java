package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphDriveItem;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphPaths;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphProperties;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetSyncProperties;

/**
 * Resolves the Daily or Monthly Timesheet file from local disk or SharePoint.
 */
@Component
public class TimesheetSourceResolver {

    private final TimesheetSyncProperties properties;
    private final MicrosoftGraphProperties graphProperties;
    private final ObjectProvider<MicrosoftGraphService> graph;
    private final ResourceLoader resources;

    /**
     * @param properties sync settings
     * @param graphProperties SharePoint site / prefix
     * @param graph Graph client when SharePoint is used
     * @param resources Spring resources
     */
    public TimesheetSourceResolver(
            TimesheetSyncProperties properties,
            MicrosoftGraphProperties graphProperties,
            ObjectProvider<MicrosoftGraphService> graph,
            ResourceLoader resources) {
        this.properties = properties;
        this.graphProperties = graphProperties;
        this.graph = graph;
        this.resources = resources;
    }

    /**
     * Opens the configured source for a kind.
     *
     * @param kind DAILY or MONTHLY
     * @return opened source
     */
    public Source open(String kind) {
        if ("graph".equalsIgnoreCase(properties.getSource())) {
            return openGraph(kind);
        }
        return openFile(kind);
    }

    private Source openFile(String kind) {
        String location = "DAILY".equals(kind) ? properties.getDailyFile() : properties.getMonthlyFile();
        Resource resource = resources.getResource(location);
        if (!resource.exists()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "SOURCE_UNAVAILABLE", "Timesheet file not found: " + location);
        }
        try {
            return new Source(resource.getFilename(), resource.getInputStream(), null, null);
        } catch (IOException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SOURCE_UNAVAILABLE",
                    "Unable to open Timesheet file: " + ex.getMessage());
        }
    }

    private Source openGraph(String kind) {
        MicrosoftGraphService client = graph.getIfAvailable();
        if (client == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SOURCE_UNAVAILABLE",
                    "Microsoft Graph is not available for Timesheet sync.");
        }
        String folder = MicrosoftGraphPaths.folderPath(
                graphProperties.envPrefix(),
                "DAILY".equals(kind) ? properties.getDailyFolder() : properties.getMonthlyFolder());
        List<GraphDriveItem> children = client.getChildrenByFolderPath(folder);
        GraphDriveItem latest = children.stream()
                .filter(GraphDriveItem::isFile)
                .filter(item -> isReportFile(item.name()))
                .max(Comparator
                        .comparing(GraphDriveItem::lastModifiedDateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(GraphDriveItem::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "SOURCE_UNAVAILABLE",
                        "No Timesheet file found in " + folder));
        return new Source(
                latest.name(),
                client.getDriveItemContentById(latest.id()),
                latest.id(),
                latest.eTag() == null ? null : latest.eTag());
    }

    private static boolean isReportFile(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".csv");
    }

    /**
     * Opened Timesheet file.
     *
     * @param fileName file name
     * @param content stream
     * @param driveItemId Graph id when from SharePoint
     * @param etag Graph etag when from SharePoint
     */
    public record Source(String fileName, InputStream content, String driveItemId, String etag) {
    }
}
