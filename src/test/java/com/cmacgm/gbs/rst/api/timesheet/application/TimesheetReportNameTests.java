package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TimesheetReportNameTests {

    @Test
    void parsesDailyConventionAndIgnoresTimestamp() {
        TimesheetReportName.Parsed parsed = TimesheetReportName.parse(
                        "Daily Raw Data of 2026-08-31 - GBS CHINA 20260901093000662.xlsx")
                .orElseThrow();
        assertThat(parsed.kind()).isEqualTo("DAILY");
        assertThat(parsed.syncDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(parsed.region()).isEqualTo("GBS CHINA");
        assertThat(parsed.revision()).isEqualTo(TimesheetReportName.BASE_REVISION);
        assertThat(TimesheetReportName.parse("Daily Raw Data of 2026-08-31 - GBS CHINA.xlsx"))
                .map(TimesheetReportName.Parsed::syncDate)
                .contains(LocalDate.of(2026, 8, 31));
    }

    @Test
    void parsesMonthlyConventionAsMonthEnd() {
        TimesheetReportName.Parsed parsed = TimesheetReportName.parse(
                        "Monthly Report of 202607(GBS CHINA) 20260831114937079.xlsx")
                .orElseThrow();
        assertThat(parsed.kind()).isEqualTo("MONTHLY");
        assertThat(parsed.syncDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(parsed.region()).isEqualTo("GBS CHINA");
        assertThat(parsed.revision()).isEqualTo(TimesheetReportName.BASE_REVISION);
    }

    @Test
    void treatsRevisionWithoutNumberAsZero() {
        TimesheetReportName.Parsed parsed = TimesheetReportName.parse(
                        "Monthly Report of 202607 Revision(GBS CHINA) 20260831134830955.xlsx")
                .orElseThrow();
        assertThat(parsed.revision()).isEqualTo(TimesheetReportName.FIRST_REVISION);
        assertThat(TimesheetReportName.parse("Monthly Report of 202607 Revision 2(GBS CHINA).xlsx")
                        .orElseThrow()
                        .revision())
                .isEqualTo(2);
    }

    @Test
    void acceptsAnyConfiguredCenter() {
        TimesheetReportName.Parsed daily = TimesheetReportName.parse(
                        "Daily Raw Data of 2026-08-31 - GBS INDIA.xlsx")
                .orElseThrow();
        assertThat(daily.kind()).isEqualTo("DAILY");
        assertThat(daily.region()).isEqualTo("GBS INDIA");

        TimesheetReportName.Parsed monthly = TimesheetReportName.parse(
                        "Monthly Report of 202607(GBS PHILIPPINES).xlsx")
                .orElseThrow();
        assertThat(monthly.kind()).isEqualTo("MONTHLY");
        assertThat(monthly.region()).isEqualTo("GBS PHILIPPINES");
        assertThat(TimesheetReportName.parse("Daily Raw Data of 2026-08-31 - gbs costa rica.xlsx")
                        .orElseThrow()
                        .region())
                .isEqualTo("GBS COSTA RICA");
    }

    @Test
    void acceptsConventionCsv() {
        assertThat(TimesheetReportName.parse("Daily Raw Data of 2026-08-31 - GBS CHINA.csv")
                        .orElseThrow()
                        .kind())
                .isEqualTo("DAILY");
        assertThat(TimesheetReportName.parse("Monthly Report of 202606(GBS INDIA).csv")
                        .orElseThrow()
                        .region())
                .isEqualTo("GBS INDIA");
    }

    @Test
    void acceptsUniqueCenterDespiteDownloadSuffix() {
        TimesheetReportName.Parsed daily = TimesheetReportName.parse(
                        "Daily Raw Data of 2026-09-13 - GBS CHINA 20260914144004218 (2).xlsx")
                .orElseThrow();
        assertThat(daily.kind()).isEqualTo("DAILY");
        assertThat(daily.syncDate()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(daily.region()).isEqualTo("GBS CHINA");

        TimesheetReportName.Parsed monthly = TimesheetReportName.parse(
                        "Monthly Report of 202607(GBS INDIA) 20260831114937079 (2).xlsx")
                .orElseThrow();
        assertThat(monthly.kind()).isEqualTo("MONTHLY");
        assertThat(monthly.syncDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(monthly.region()).isEqualTo("GBS INDIA");
        assertThat(TimesheetReportName.parse("Daily Raw Data of 2026-08-31 - GBS CHINA (2).xlsx")
                        .orElseThrow()
                        .region())
                .isEqualTo("GBS CHINA");
    }

    @Test
    void rejectsOldNamesUnknownCenterAndRandomNames() {
        assertThat(TimesheetReportName.parse("Daily Report of 20260727(GBS CHINA).xlsx")).isEmpty();
        assertThat(TimesheetReportName.parse("Daily Raw Data of 2026-08-31 - GBS MARS.xlsx")).isEmpty();
        assertThat(TimesheetReportName.parse("Daily Raw Data of 2026-08-31 - GBS CHINA GBS INDIA.xlsx"))
                .isEmpty();
        assertThat(TimesheetReportName.parse("timesheet.xlsx")).isEmpty();
    }
}
