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

        List<TimesheetSourceResolver.NamedFile> chosen =
                TimesheetSourceResolver.chooseAll("DAILY", List.of(older, newer), "Daily");

        assertThat(chosen).extracting(file -> file.item().id()).containsExactly("new");
        assertThat(chosen.getFirst().parsed().syncDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void ignoresTimestampWhenMatchingDailyName() {
        GraphDriveItem stamped = file(
                "Daily Raw Data of 2026-08-31 - GBS CHINA 20260901093000662.xlsx", "stamped");

        List<TimesheetSourceResolver.NamedFile> chosen =
                TimesheetSourceResolver.chooseAll("DAILY", List.of(stamped), "Daily");

        assertThat(chosen).extracting(file -> file.item().id()).containsExactly("stamped");
        assertThat(chosen.getFirst().parsed().syncDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void picksHighestMonthlyRevisionForTheSameMonth() {
        GraphDriveItem original = file("Monthly Report of 202607(GBS CHINA).xlsx", "base");
        GraphDriveItem revisionZero = file("Monthly Report of 202607 Revision(GBS CHINA).xlsx", "rev0");
        GraphDriveItem revisionTwo = file("Monthly Report of 202607 Revision 2(GBS CHINA).xlsx", "rev2");

        List<TimesheetSourceResolver.NamedFile> chosen = TimesheetSourceResolver.chooseAll(
                "MONTHLY", List.of(original, revisionTwo, revisionZero), "Monthly");

        assertThat(chosen).extracting(file -> file.item().id()).containsExactly("rev2");
        assertThat(chosen.getFirst().parsed().revision()).isEqualTo(2);
    }

    @Test
    void ignoresUnknownCenterAndNonConventionNames() {
        GraphDriveItem valid = file("Daily Raw Data of 2026-08-31 - GBS CHINA.xlsx", "ok");
        GraphDriveItem unknownCenter = file("Daily Raw Data of 2026-09-01 - GBS MARS.xlsx", "mars");
        GraphDriveItem oldName = file("Daily Report of 20260727(GBS CHINA).xlsx", "legacy");
        GraphDriveItem random = file("latest.xlsx", "random");

        List<TimesheetSourceResolver.NamedFile> chosen =
                TimesheetSourceResolver.chooseAll("DAILY", List.of(unknownCenter, random, oldName, valid), "Daily");

        assertThat(chosen).extracting(file -> file.item().id()).containsExactly("ok");
    }

    @Test
    void keepsNewestFilePerConfiguredCenter() {
        GraphDriveItem china = file("Daily Raw Data of 2026-08-30 - GBS CHINA.xlsx", "china");
        GraphDriveItem indiaOlder = file("Daily Raw Data of 2026-08-30 - GBS INDIA.xlsx", "india-old");
        GraphDriveItem india = file("Daily Raw Data of 2026-08-31 - GBS INDIA.xlsx", "india");

        List<TimesheetSourceResolver.NamedFile> chosen =
                TimesheetSourceResolver.chooseAll("DAILY", List.of(china, indiaOlder, india), "Daily");

        assertThat(chosen)
                .extracting(file -> file.item().id(), file -> file.parsed().region())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("china", "GBS CHINA"),
                        org.assertj.core.groups.Tuple.tuple("india", "GBS INDIA"));
    }

    @Test
    void rejectsEmptyFolder() {
        assertThatThrownBy(() -> TimesheetSourceResolver.chooseAll("MONTHLY", List.of(), "Monthly"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo("SOURCE_UNAVAILABLE");
    }

    private static GraphDriveItem file(String name, String id) {
        return new GraphDriveItem(
                id, name, null, 1L, null, new GraphFile("application/vnd.ms-excel"), null, "etag-" + id, null);
    }
}
