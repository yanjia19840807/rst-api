package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphDriveItem;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
import com.cmacgm.gbs.rst.api.graph.RstSharePointProperties;

/**
 * Resolves the Daily or Monthly Timesheet file from SharePoint.
 */
@Component
public class TimesheetSourceResolver {

    private final RstSharePointProperties sharePoint;
    private final MicrosoftGraphService graph;

    /**
     * @param sharePoint RST SharePoint folders
     * @param graph Graph client
     */
    public TimesheetSourceResolver(RstSharePointProperties sharePoint, MicrosoftGraphService graph) {
        this.sharePoint = sharePoint;
        this.graph = graph;
    }

    /**
     * Opens the latest report in the configured SharePoint folder.
     *
     * @param kind DAILY or MONTHLY
     * @return opened source
     */
    public Source open(String kind) {
        String folder = "DAILY".equals(kind) ? sharePoint.dailyFolder() : sharePoint.monthlyFolder();
        List<GraphDriveItem> children = graph.getChildrenByFolderPath(folder);
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
                graph.getDriveItemContentById(latest.id()),
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
     * @param driveItemId Graph id
     * @param etag Graph etag
     */
    public record Source(String fileName, InputStream content, String driveItemId, String etag) {
    }
}
