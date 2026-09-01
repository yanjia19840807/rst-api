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
        GraphDriveItem older = file("Daily Raw Data of 2026-08-30 - GBS CHINA.xlsx", "old");
        GraphDriveItem newer = file("Daily Raw Data of 2026-08-31 - GBS CHINA.xlsx", "new");

        TimesheetSourceResolver.NamedFile chosen =
                TimesheetSourceResolver.choose("DAILY", List.of(older, newer), "Daily");

        assertThat(chosen.item().id()).isEqualTo("new");
        assertThat(chosen.parsed().syncDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void ignoresTimestampWhenMatchingDailyName() {
        GraphDriveItem stamped = file(
                "Daily Raw Data of 2026-08-31 - GBS CHINA 20260901093000662.xlsx", "stamped");

        TimesheetSourceResolver.NamedFile chosen =
                TimesheetSourceResolver.choose("DAILY", List.of(stamped), "Daily");

        assertThat(chosen.item().id()).isEqualTo("stamped");
        assertThat(chosen.parsed().syncDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void picksHighestMonthlyRevisionForTheSameMonth() {
        GraphDriveItem original = file("Monthly Report of 202607(GBS CHINA).xlsx", "base");
        GraphDriveItem revisionZero = file("Monthly Report of 202607 Revision(GBS CHINA).xlsx", "rev0");
        GraphDriveItem revisionTwo = file("Monthly Report of 202607 Revision 2(GBS CHINA).xlsx", "rev2");

        TimesheetSourceResolver.NamedFile chosen = TimesheetSourceResolver.choose(
                "MONTHLY", List.of(original, revisionTwo, revisionZero), "Monthly");

        assertThat(chosen.item().id()).isEqualTo("rev2");
        assertThat(chosen.parsed().revision()).isEqualTo(2);
    }

    @Test
    void ignoresOtherRegionAndNonConventionNames() {
        GraphDriveItem valid = file("Daily Raw Data of 2026-08-31 - GBS CHINA.xlsx", "ok");
        GraphDriveItem otherRegion = file("Daily Raw Data of 2026-09-01 - GBS INDIA.xlsx", "india");
        GraphDriveItem oldName = file("Daily Report of 20260727(GBS CHINA).xlsx", "legacy");
        GraphDriveItem random = file("latest.xlsx", "random");

        TimesheetSourceResolver.NamedFile chosen =
                TimesheetSourceResolver.choose("DAILY", List.of(otherRegion, random, oldName, valid), "Daily");

        assertThat(chosen.item().id()).isEqualTo("ok");
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
