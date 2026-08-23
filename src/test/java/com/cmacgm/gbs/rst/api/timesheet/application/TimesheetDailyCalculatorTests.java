package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.HcValue;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.ReportRow;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import org.junit.jupiter.api.Test;

class TimesheetDailyCalculatorTests {

    private final TimesheetDailyCalculator calculator = new TimesheetDailyCalculator();

    @Test
    void personKeepsManagementPositionAndCenters() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(row(
                        "S00000001",
                        "EMP-1",
                        "Agent One",
                        "EMP-POS-1",
                        "S00000002",
                        "SUP-1",
                        "Supervisor One",
                        "POS-SUP-1",
                        "S00000003",
                        "SRM-1",
                        "Manager One",
                        "POS-SRM-1",
                        "S00000004",
                        "DH-1",
                        "Head One",
                        "POS-DH-1",
                        "Kuala Lumpur")),
                Instant.parse("2026-08-23T00:00:00Z"));

        assertThat(result.issues()).isEmpty();
        assertThat(result.people())
                .extracting(TimesheetPerson::getCcgid, TimesheetPerson::getEmpPositionId, TimesheetPerson::getCenter)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("S00000001", "EMP-POS-1", "Kuala Lumpur"),
                        org.assertj.core.groups.Tuple.tuple("S00000002", "POS-SUP-1", "Kuala Lumpur"),
                        org.assertj.core.groups.Tuple.tuple("S00000003", "POS-SRM-1", "Kuala Lumpur"),
                        org.assertj.core.groups.Tuple.tuple("S00000004", "POS-DH-1", "Kuala Lumpur"));
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String empName,
            String empPositionId,
            String supervisorCcgid,
            String supervisorId,
            String supervisorName,
            String supervisorPositionId,
            String srManagerCcgid,
            String srManagerId,
            String srManagerName,
            String srManagerPositionId,
            String domainHeadCcgid,
            String domainHeadId,
            String domainHeadName,
            String domainHeadPositionId,
            String center) {
        return new ReportRow(
                2,
                LocalDate.of(2026, 7, 27),
                null,
                empId,
                empCcgid,
                empName,
                empPositionId,
                supervisorId,
                supervisorCcgid,
                supervisorName,
                supervisorPositionId,
                srManagerId,
                srManagerCcgid,
                srManagerName,
                srManagerPositionId,
                domainHeadId,
                domainHeadCcgid,
                domainHeadName,
                domainHeadPositionId,
                center,
                "Site",
                "Finance",
                "PL1",
                "PL2",
                "PL3",
                "PL3 Name",
                "CMA",
                "MY",
                new HcValue(BigDecimal.ONE, false));
    }
}
