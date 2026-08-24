package com.cmacgm.gbs.rst.api.associateddata.application;

import org.springframework.stereotype.Service;

import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphProperties;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
import com.cmacgm.gbs.rst.api.graph.RstSharePointProperties;

/**
 * Serves blank import Excel templates from {@code rst.sharepoint.root}/Template.
 */
@Service
public class ImportTemplateService {

    /**
     * Known import templates stored under the SharePoint Template folder.
     */
    public enum Kind {
        CALENDAR("calendar-template.xlsx"),
        VOLUME_MONTHLY("volume-monthly-template.xlsx"),
        VOLUME_DAILY("volume-daily-template.xlsx"),
        VOLUME_SLOT("volume-slot-template.xlsx");

        private final String fileName;

        Kind(String fileName) {
            this.fileName = fileName;
        }

        /**
         * @return SharePoint file name
         */
        public String fileName() {
            return fileName;
        }
    }

    private final RstSharePointProperties sharePoint;
    private final MicrosoftGraphProperties graphProperties;
    private final MicrosoftGraphService graph;
    private final HolidayExcelService holidayExcel;
    private final VolumeExcelService volumeExcel;

    /**
     * @param sharePoint RST SharePoint folders
     * @param graphProperties Graph enable / credentials
     * @param graph Graph client
     * @param holidayExcel local holiday blank used when Graph is off
     * @param volumeExcel local volume blanks used when Graph is off
     */
    public ImportTemplateService(
            RstSharePointProperties sharePoint,
            MicrosoftGraphProperties graphProperties,
            MicrosoftGraphService graph,
            HolidayExcelService holidayExcel,
            VolumeExcelService volumeExcel) {
        this.sharePoint = sharePoint;
        this.graphProperties = graphProperties;
        this.graph = graph;
        this.holidayExcel = holidayExcel;
        this.volumeExcel = volumeExcel;
    }

    /**
     * Downloads a template. Uses SharePoint when Graph is configured; otherwise a local blank.
     *
     * @param kind template kind
     * @return xlsx bytes
     */
    public byte[] download(Kind kind) {
        if (graphProperties.enabled() && graphProperties.hasCredentials()) {
            return graph.getFileBytes(sharePoint.templateFolder(), kind.fileName());
        }
        return generate(kind);
    }

    /**
     * Builds the four blank workbooks used to seed SharePoint.
     *
     * @param kind template kind
     * @return xlsx bytes
     */
    public byte[] generate(Kind kind) {
        return switch (kind) {
            case CALENDAR -> holidayExcel.exportBlank();
            case VOLUME_MONTHLY -> volumeExcel.exportMonthlyBlank();
            case VOLUME_DAILY -> volumeExcel.exportDailyBlank();
            case VOLUME_SLOT -> volumeExcel.exportSlotBlank();
        };
    }
}
