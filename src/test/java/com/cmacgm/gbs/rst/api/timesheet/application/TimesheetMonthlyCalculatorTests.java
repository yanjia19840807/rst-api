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
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;
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
                        TimesheetAssignment::getEmpPositionId,
                        TimesheetAssignment::getSupervisorPositionId,
                        TimesheetAssignment::getPl3Code,
                        TimesheetAssignment::getCenter)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("EMP-1", "POS-SUP-1", "PL3", "Kuala Lumpur"));
        assertThat(result.scopes())
                .extracting(TimesheetScope::getSupervisorPositionId, TimesheetScope::getCenter, TimesheetScope::getPl3Code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("POS-SUP-1", "Kuala Lumpur", "PL3"));
        assertThat(result.kpis())
                .extracting(TimesheetKpi::getSupervisorPositionId, TimesheetKpi::getCenter, TimesheetKpi::getHc)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("POS-SUP-1", "Kuala Lumpur", new BigDecimal("2.0")));
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
        assertThat(result.assignments())
                .extracting(TimesheetAssignment::getSupervisorPositionId)
                .containsExactly("POS-SUP-1", "POS-SUP-2");
    }

    @Test
    void allowsSameSeatUnderDifferentSupervisorsOnDifferentPl3() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3-A", "Kuala Lumpur", "1"),
                        row(
                                "S00000001",
                                "EMP-1",
                                "POS-SUP-2",
                                "PL3-B",
                                "Kuala Lumpur",
                                "1",
                                "management",
                                "non-productive")),
                Instant.parse("2026-08-24T00:00:00Z"));

        assertThat(result.issues()).isEmpty();
        assertThat(result.assignments())
                .extracting(TimesheetAssignment::getEmpPositionId, TimesheetAssignment::getSupervisorPositionId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("EMP-1", "POS-SUP-1"),
                        org.assertj.core.groups.Tuple.tuple("EMP-1", "POS-SUP-2"));
    }

    @Test
    void writesMissingFieldOnIncompleteRowsRegardlessOfType() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "Kuala Lumpur", "1"),
                        row("S00000002", "EMP-2", "POS-SUP-1", "", "Kuala Lumpur", "1", "management", "non-productive")),
                Instant.parse("2026-08-24T00:00:00Z"));

        assertThat(result.issues())
                .extracting(TimesheetSyncIssue::getMessage)
                .contains("Missing pl3_code.");
        assertThat(result.assignments())
                .extracting(TimesheetAssignment::getEmpPositionId)
                .containsExactly("EMP-1");
    }

    @Test
    void requiresFieldsOnProductionLines() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000001", "EMP-1", "POS-SUP-1", "", "Kuala Lumpur", "1", "production", "productive")),
                Instant.parse("2026-08-24T00:00:00Z"));

        assertThat(result.issues())
                .extracting(TimesheetSyncIssue::getMessage)
                .contains("Missing pl3_code.");
    }

    @Test
    void persistsScopeAssignmentAndKpiFromEveryCompleteRow() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "Kuala Lumpur", "1"),
                        row(
                                "S00000002",
                                "EMP-2",
                                "POS-SUP-2",
                                "PL3",
                                "Kuala Lumpur",
                                "1",
                                "management",
                                "non-productive"),
                        row(
                                "S00000003",
                                "EMP-3",
                                "182894",
                                "344",
                                "GBS CHINA",
                                "0.5",
                                "production",
                                "non-productive")),
                Instant.parse("2026-08-24T00:00:00Z"));

        assertThat(result.issues()).isEmpty();
        assertThat(result.assignments())
                .extracting(TimesheetAssignment::getEmpPositionId, TimesheetAssignment::getSupervisorPositionId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("EMP-1", "POS-SUP-1"),
                        org.assertj.core.groups.Tuple.tuple("EMP-2", "POS-SUP-2"),
                        org.assertj.core.groups.Tuple.tuple("EMP-3", "182894"));
        assertThat(result.scopes())
                .extracting(TimesheetScope::getSupervisorPositionId, TimesheetScope::getPl3Code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POS-SUP-1", "PL3"),
                        org.assertj.core.groups.Tuple.tuple("POS-SUP-2", "PL3"),
                        org.assertj.core.groups.Tuple.tuple("182894", "344"));
        assertThat(result.kpis())
                .extracting(TimesheetKpi::getSupervisorPositionId, TimesheetKpi::getHc)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POS-SUP-1", new BigDecimal("1")),
                        org.assertj.core.groups.Tuple.tuple("POS-SUP-2", new BigDecimal("1")),
                        org.assertj.core.groups.Tuple.tuple("182894", new BigDecimal("0.5")));
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String supervisorPositionId,
            String pl3Code,
            String center,
            String hc) {
        return row(empCcgid, empId, supervisorPositionId, pl3Code, center, hc, "production", "productive");
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String supervisorPositionId,
            String pl3Code,
            String center,
            String hc,
            String managementOrProduction,
            String costType) {
        return new ReportRow(
                2,
                null,
                YearMonth.of(2026, 6).atEndOfMonth(),
                empId,
                empCcgid,
                "Agent",
                empCcgid.toLowerCase() + "@dev.local",
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
                new HcValue(new BigDecimal(hc), false),
                managementOrProduction,
                costType);
    }
}
