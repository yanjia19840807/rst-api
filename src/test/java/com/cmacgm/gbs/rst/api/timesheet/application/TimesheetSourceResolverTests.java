package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphDriveItem;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.GraphFile;
import org.junit.jupiter.api.Test;

class TimesheetSourceResolverTests {

    @Test
    void picksNewestFilenameDateNotLastModified() {
        GraphDriveItem older = file("Daily Report of 20260726(GBS CHINA).xlsx", "old");
        GraphDriveItem newer = file("Daily Report of 20260727(GBS CHINA).xlsx", "new");

        TimesheetSourceResolver.NamedFile chosen =
                TimesheetSourceResolver.choose("DAILY", List.of(older, newer), "Daily");

        assertThat(chosen.item().id()).isEqualTo("new");
        assertThat(chosen.parsed().syncDate()).isEqualTo(LocalDate.of(2026, 7, 27));
    }

    @Test
    void ignoresOtherRegionAndNonConventionNames() {
        GraphDriveItem valid = file("Daily Report of 20260727(GBS CHINA).xlsx", "ok");
        GraphDriveItem otherRegion = file("Daily Report of 20260728(GBS INDIA).xlsx", "india");
        GraphDriveItem random = file("latest.xlsx", "random");

        TimesheetSourceResolver.NamedFile chosen =
                TimesheetSourceResolver.choose("DAILY", List.of(otherRegion, random, valid), "Daily");

        assertThat(chosen.item().id()).isEqualTo("ok");
    }

    @Test
    void rejectsTwoFilesOnTheSameBusinessDate() {
        assertThatThrownBy(() -> TimesheetSourceResolver.choose(
                        "DAILY",
                        List.of(
                                file("Daily Report of 20260727(GBS CHINA).xlsx", "a"),
                                file("Daily Report of 20260727(GBS china).xlsx", "b")),
                        "Daily"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo("AMBIGUOUS_SOURCE");
    }

    @Test
    void rejectsEmptyFolder() {
        assertThatThrownBy(() -> TimesheetSourceResolver.choose("MONTHLY", List.of(), "Monthly"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo("SOURCE_UNAVAILABLE");
    }

    private static GraphDriveItem file(String name, String id) {
        return new GraphDriveItem(
                id, name, null, 1L, null, new GraphFile("application/vnd.ms-excel"), null, "etag-" + id, null);
    }
}
