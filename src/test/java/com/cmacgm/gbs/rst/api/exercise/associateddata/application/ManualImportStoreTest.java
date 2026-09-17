package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.FileArtifact;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphProperties;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphService;
import com.cmacgm.gbs.rst.api.mail.application.MailProperties;
import com.cmacgm.gbs.rst.api.timesheet.config.TimesheetSharePointProperties;

class ManualImportStoreTest {

    @Test
    void store_fallsBackToStubWhenGraphCredentialsMissing() {
        MicrosoftGraphProperties graphOff = new MicrosoftGraphProperties(null, "", "", "");
        TimesheetSharePointProperties sharePoint = new TimesheetSharePointProperties(null, null, null);
        ManualImportStore store = new ManualImportStore(
                sharePoint,
                graphOff,
                new MicrosoftGraphService(graphOff, sharePoint, new MailProperties(false, null, null)));

        FileArtifact artifact = store.store(
                ManualImportStore.Module.VOLUME,
                "MONTHLY_VOLUME",
                "EXERCISE",
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                "volume-monthly.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1, 2, 3},
                "C001",
                Instant.parse("2026-09-17T08:27:22.123Z"));

        assertTrue(artifact.getSharepointDriveItemId().startsWith("stub-"));
        assertEquals("volume-monthly.xlsx", artifact.getFileName());
        assertEquals(3L, artifact.getSizeBytes());
    }

    @Test
    void storedFileName_includesStampKindAndSanitizedOriginal() {
        UUID exerciseId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String name = ManualImportStore.storedFileName(
                Instant.parse("2026-09-17T08:27:22.123Z"),
                "MONTHLY_VOLUME",
                exerciseId,
                "C:/tmp/volume-monthly.xlsx");
        assertEquals("20260917T082722123Z_MONTHLY_VOLUME_aaaaaaaa_volume-monthly.xlsx", name);
    }

    @Test
    void sanitizeFileName_stripsPathAndIllegalCharacters() {
        assertEquals("import.xlsx", ManualImportStore.sanitizeFileName("  "));
        assertEquals("my_file.xlsx", ManualImportStore.sanitizeFileName("dir/my:file.xlsx"));
    }
}
