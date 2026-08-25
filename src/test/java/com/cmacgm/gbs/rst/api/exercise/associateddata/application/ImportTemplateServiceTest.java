package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.exercise.associateddata.application.ImportTemplateService.Kind;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphProperties;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
import com.cmacgm.gbs.rst.api.graph.RstSharePointProperties;

class ImportTemplateServiceTest {

    @Test
    void download_fallsBackToGeneratedBlankWhenGraphIsOff() {
        MicrosoftGraphProperties graphOff = new MicrosoftGraphProperties(
                false, null, "", "", "", null, null);
        ImportTemplateService templates = new ImportTemplateService(
                new RstSharePointProperties(null),
                graphOff,
                new MicrosoftGraphService(graphOff),
                new HolidayExcelService(),
                new VolumeExcelService());

        for (Kind kind : Kind.values()) {
            byte[] body = templates.download(kind);
            assertTrue(body.length > 0, kind.fileName());
        }
    }
}
