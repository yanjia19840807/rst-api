package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.HcValue;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.ReportRow;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetAssignment;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import org.junit.jupiter.api.Test;

class TimesheetMonthlyCalculatorTests {

    private final TimesheetMonthlyCalculator calculator = new TimesheetMonthlyCalculator();

    @Test
    void buildsAssignmentScopeAndKpiFromMonthlyRows() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "Kuala Lumpur", "1.5"),
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "Kuala Lumpur", "0.5")),
                Instant.parse("2026-08-24T00:00:00Z"));

        assertThat(result.issues()).isEmpty();
        assertThat(result.assignments())
                .extracting(
                        TimesheetAssignment::getEmpCcgid,
                        TimesheetAssignment::getSupervisorPositionId,
                        TimesheetAssignment::getPl3Code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("S00000001", "POS-SUP-1", "PL3"));
        assertThat(result.scopes())
                .extracting(TimesheetScope::getSupervisorPositionId, TimesheetScope::getCenter, TimesheetScope::getPl3Code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("POS-SUP-1", "Kuala Lumpur", "PL3"));
        assertThat(result.kpis())
                .extracting(TimesheetKpi::getSupervisorPositionId, TimesheetKpi::getHc)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("POS-SUP-1", new BigDecimal("2.0")));
    }

    @Test
    void flagsEmployeeAssignedToMultipleSupervisors() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "Kuala Lumpur", "1"),
                        row("S00000001", "EMP-1", "POS-SUP-2", "PL3", "Kuala Lumpur", "1")),
                Instant.parse("2026-08-24T00:00:00Z"));

        assertThat(result.issues())
                .extracting(issue -> issue.getCode())
                .contains("ASSIGNMENT_CONFLICT");
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String supervisorPositionId,
            String pl3Code,
            String center,
            String hc) {
        return new ReportRow(
                2,
                null,
                YearMonth.of(2026, 6).atEndOfMonth(),
                empId,
                empCcgid,
                "Agent",
                empId,
                "SUP-1",
                "S00000002",
                "Supervisor",
                supervisorPositionId,
                "SRM-1",
                "S00000003",
                "Manager",
                "POS-SRM-1",
                "DH-1",
                "S00000004",
                "Head",
                "POS-DH-1",
                center,
                "Site",
                "Finance",
                "PL1",
                "PL2",
                pl3Code,
                "PL3 Name",
                "CMA",
                "MY",
                new HcValue(new BigDecimal(hc), false));
    }
}
