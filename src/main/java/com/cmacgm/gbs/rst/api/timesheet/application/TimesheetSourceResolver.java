package com.cmacgm.gbs.rst.api.timesheet.application;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphDriveItem;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
import com.cmacgm.gbs.rst.api.graph.RstSharePointProperties;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportName.Parsed;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * Resolves Daily / Monthly files from SharePoint and stores LTH uploads under Manual.
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
     * Opens the newest named report in the Daily or Monthly folder.
     *
     * @param kind DAILY or MONTHLY
     * @return opened source
     */
    public Source open(String kind) {
        String folder = "DAILY".equals(kind) ? sharePoint.dailyFolder() : sharePoint.monthlyFolder();
        List<GraphDriveItem> children = graph.getChildrenByFolderPath(folder);
        NamedFile chosen = choose(kind, children, folder);
        GraphDriveItem item = chosen.item();
        return new Source(
                item.name(),
                graph.getDriveItemContentById(item.id()),
                item.id(),
                item.eTag() == null ? null : item.eTag(),
                "SHAREPOINT",
                chosen.parsed().syncDate());
    }

    /**
     * Uploads a Manual file and returns a source with Graph identity.
     *
     * @param fileName original name
     * @param content file bytes
     * @return stored source
     */
    public Source storeManual(String fileName, byte[] content) {
        graph.ensureFolder(sharePoint.manualFolder());
        GraphDriveItem stored = graph.putDriveItemContent(
                sharePoint.manualFolder(),
                fileName,
                content,
                MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        Parsed parsed = TimesheetReportName.parse(fileName).orElse(null);
        return new Source(
                stored.name() == null ? fileName : stored.name(),
                new ByteArrayInputStream(content),
                stored.id(),
                stored.eTag() == null ? null : stored.eTag(),
                "MANUAL",
                parsed == null ? null : parsed.syncDate());
    }

    /**
     * Opened Timesheet file.
     *
     * @param fileName file name
     * @param content stream
     * @param driveItemId Graph id
     * @param etag Graph etag
     * @param sourceType SHAREPOINT or MANUAL
     * @param filenameDate business date from the name
     */
    public record Source(
            String fileName,
            InputStream content,
            String driveItemId,
            String etag,
            String sourceType,
            LocalDate filenameDate) {
    }

    static NamedFile choose(String kind, List<GraphDriveItem> children, String folder) {
        List<NamedFile> named = children.stream()
                .filter(GraphDriveItem::isFile)
                .map(item -> TimesheetReportName.parse(item.name())
                        .filter(parsed -> parsed.kind().equals(kind))
                        .map(parsed -> new NamedFile(item, parsed))
                        .orElse(null))
                .filter(file -> file != null)
                .toList();
        if (named.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    TimesheetSyncErrorCode.SOURCE_UNAVAILABLE.code(),
                    "No Timesheet file found in " + folder);
        }
        LocalDate latestDate = named.stream()
                .map(file -> file.parsed().syncDate())
                .max(Comparator.naturalOrder())
                .orElseThrow();
        List<NamedFile> sameDay = named.stream()
                .filter(file -> file.parsed().syncDate().equals(latestDate))
                .toList();
        if (sameDay.size() > 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    TimesheetSyncErrorCode.AMBIGUOUS_SOURCE.code(),
                    "Multiple Timesheet files share business date " + latestDate);
        }
        return sameDay.getFirst();
    }

    record NamedFile(GraphDriveItem item, Parsed parsed) {
    }
}
