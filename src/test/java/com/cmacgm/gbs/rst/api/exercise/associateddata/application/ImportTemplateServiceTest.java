package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.exercise.associateddata.application.ImportTemplateService.Kind;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphProperties;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
import com.cmacgm.gbs.rst.api.mail.application.MailProperties;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetSharePointProperties;

class ImportTemplateServiceTest {

    @Test
    void download_fallsBackToGeneratedBlankWhenGraphCredentialsMissing() {
        MicrosoftGraphProperties graphOff = new MicrosoftGraphProperties(null, "", "", "");
        TimesheetSharePointProperties sharePoint = new TimesheetSharePointProperties(null, null, null);
        ImportTemplateService templates = new ImportTemplateService(
                sharePoint,
                graphOff,
                new MicrosoftGraphService(graphOff, sharePoint, new MailProperties(false, null, null)),
                new HolidayExcelService(),
                new VolumeExcelService(),
                new SupportExcelService());

        for (Kind kind : Kind.values()) {
            byte[] body = templates.download(kind);
            assertTrue(body.length > 0, kind.fileName());
        }
    }
}
