package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TimesheetReportNameTests {

    @Test
    void parsesDailyConvention() {
        TimesheetReportName.Parsed parsed = TimesheetReportName.parse(
                        "Daily Report of 20260727(GBS CHINA).xlsx")
                .orElseThrow();
        assertThat(parsed.kind()).isEqualTo("DAILY");
        assertThat(parsed.syncDate()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(parsed.region()).isEqualTo("GBS CHINA");
    }

    @Test
    void parsesMonthlyConventionAsMonthEnd() {
        TimesheetReportName.Parsed parsed = TimesheetReportName.parse(
                        "Monthly Report of 202606(GBS CHINA).xlsx")
                .orElseThrow();
        assertThat(parsed.kind()).isEqualTo("MONTHLY");
        assertThat(parsed.syncDate()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void rejectsOtherRegionAndCsv() {
        assertThat(TimesheetReportName.parse("Daily Report of 20260727(GBS INDIA).xlsx")).isEmpty();
        assertThat(TimesheetReportName.parse("Daily Report of 20260727(GBS CHINA).csv")).isEmpty();
        assertThat(TimesheetReportName.parse("timesheet.xlsx")).isEmpty();
    }
}
