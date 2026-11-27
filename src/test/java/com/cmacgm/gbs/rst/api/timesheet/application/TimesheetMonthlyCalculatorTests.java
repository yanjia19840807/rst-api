package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.HcValue;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.ReportRow;
import com.cmacgm.gbs.rst.api.timesheet.domain.SupervisorDeliveryHc;
import com.cmacgm.gbs.rst.api.timesheet.domain.SupervisorProcess;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceIssue;
import org.junit.jupiter.api.Test;

class TimesheetMonthlyCalculatorTests {

    private static final GbsProcessCatalog RST_YES = GbsProcessCatalog.allowing("PL3", "PL3-A", "PL3-B", "344");

    private final TimesheetMonthlyCalculator calculator = new TimesheetMonthlyCalculator();

    @Test
    void buildsScopeAndKpiFromMonthlyRows() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS INDIA", "1.5"),
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS INDIA", "0.5")),
                Instant.parse("2026-08-24T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).isEmpty();
        assertThat(result.scopes())
                .extracting(SupervisorProcess::getSupervisorPositionId, SupervisorProcess::getCenter, SupervisorProcess::getPl3Code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("POS-SUP-1", "GBS INDIA", "PL3"));
        assertThat(result.kpis())
                .extracting(SupervisorDeliveryHc::getSupervisorPositionId, SupervisorDeliveryHc::getCenter, SupervisorDeliveryHc::getHc)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("POS-SUP-1", "GBS INDIA", new BigDecimal("2.0")));
    }

    @Test
    void keepsBothSupervisorScopesOnTheSamePl3() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS INDIA", "1"),
                        row("S00000001", "EMP-1", "POS-SUP-2", "PL3", "GBS INDIA", "1")),
                Instant.parse("2026-08-24T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).extracting(SourceIssue::getCode).doesNotContain("ASSIGNMENT_CONFLICT");
        assertThat(result.scopes())
                .extracting(SupervisorProcess::getSupervisorPositionId)
                .containsExactly("POS-SUP-1", "POS-SUP-2");
    }

    @Test
    void writesMissingFieldOnIncompleteRstRows() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS INDIA", "1"),
                        row("", "EMP-2", "POS-SUP-1", "PL3", "GBS INDIA", "1", "management", "non-productive")),
                Instant.parse("2026-08-24T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues())
                .extracting(SourceIssue::getMessage)
                .contains("Missing emp_ccgid.");
        assertThat(result.issues()).allMatch(issue -> !TimesheetRowValidator.isAdvisory(issue, "MONTHLY"));
        assertThat(result.scopes())
                .extracting(SupervisorProcess::getSupervisorPositionId)
                .containsExactly("POS-SUP-1");
    }

    @Test
    void requiresFieldsOnRstRows() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000001", "EMP-1", "", "PL3", "GBS INDIA", "1", "production", "productive")),
                Instant.parse("2026-08-24T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues())
                .extracting(SourceIssue::getMessage)
                .contains("Missing supervisor_position_id.");
    }

    @Test
    void skipsNonRstRowsBeforeFieldAndCenterChecks() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS INDIA", "1"),
                        row("", "EMP-2", "", "SKIP", "GBS VIETNAM", "1")),
                Instant.parse("2026-08-24T00:00:00Z"),
                java.time.LocalDate.of(2026, 6, 30),
                RST_YES);

        assertThat(result.issues()).isEmpty();
        assertThat(result.scopes()).extracting(SupervisorProcess::getPl3Code).containsExactly("PL3");
    }

    @Test
    void acceptsCenterFromTheFileWithoutCatalogCheck() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS VIETNAM", "1")),
                Instant.parse("2026-08-24T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).isEmpty();
        assertThat(result.scopes()).extracting(SupervisorProcess::getCenter).containsExactly("GBS VIETNAM");
    }

    @Test
    void mapsScopeAndKpiFromEveryCompleteRow() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS INDIA", "1"),
                        row(
                                "S00000002",
                                "EMP-2",
                                "POS-SUP-2",
                                "PL3",
                                "GBS INDIA",
                                "1",
                                "management",
                                "non-productive"),
                        row(
                                "S00000003",
                                "EMP-3",
                                "182894",
                                "344",
                                "GBS INDIA",
                                "0.5",
                                "production",
                                "non-productive")),
                Instant.parse("2026-08-24T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).isEmpty();
        assertThat(result.scopes())
                .extracting(SupervisorProcess::getSupervisorPositionId, SupervisorProcess::getPl3Code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POS-SUP-1", "PL3"),
                        org.assertj.core.groups.Tuple.tuple("POS-SUP-2", "PL3"),
                        org.assertj.core.groups.Tuple.tuple("182894", "344"));
        assertThat(result.kpis())
                .extracting(SupervisorDeliveryHc::getSupervisorPositionId, SupervisorDeliveryHc::getHc)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POS-SUP-1", new BigDecimal("1")),
                        org.assertj.core.groups.Tuple.tuple("POS-SUP-2", new BigDecimal("1")),
                        org.assertj.core.groups.Tuple.tuple("182894", new BigDecimal("0.5")));
    }

    @Test
    void skipsCompleteRowsWhenPl3IsNotRstApplicable() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS INDIA", "1"),
                        row("S00000002", "EMP-2", "POS-SUP-1", "SKIP", "GBS INDIA", "1")),
                Instant.parse("2026-08-24T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).isEmpty();
        assertThat(result.scopes()).extracting(SupervisorProcess::getPl3Code).containsExactly("PL3");
        assertThat(result.kpis()).extracting(SupervisorDeliveryHc::getPl3Code).containsExactly("PL3");
    }

    @Test
    void ignoresFilenameDateOnRstRows() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS INDIA", "1")),
                Instant.parse("2026-08-24T00:00:00Z"),
                java.time.LocalDate.of(2026, 7, 31),
                RST_YES);

        assertThat(result.issues()).isEmpty();
        assertThat(result.syncDate()).isEqualTo(java.time.LocalDate.of(2026, 7, 31));
        assertThat(result.scopes()).extracting(SupervisorProcess::getPl3Code).containsExactly("PL3");
    }

    @Test
    void usesFilenameCenterInsteadOfRowCenter() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS VIETNAM", "1")),
                Instant.parse("2026-08-24T00:00:00Z"),
                java.time.LocalDate.of(2026, 6, 30),
                RST_YES,
                "GBS INDIA");

        assertThat(result.issues()).isEmpty();
        assertThat(result.scopes()).extracting(SupervisorProcess::getCenter).containsExactly("GBS INDIA");
        assertThat(result.kpis()).extracting(SupervisorDeliveryHc::getCenter).containsExactly("GBS INDIA");
    }

    @Test
    void flagsInvalidHcOnRstRows() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS INDIA", new HcValue(null, true))),
                Instant.parse("2026-08-24T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues())
                .extracting(SourceIssue::getCode, SourceIssue::getMessage)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("INVALID_HC", "hc is not a valid number."));
        assertThat(result.kpis()).isEmpty();
    }

    @Test
    void skipsInvalidHcOnNonRstRows() {
        UUID runId = UUID.randomUUID();
        TimesheetMonthlyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "POS-SUP-1", "PL3", "GBS INDIA", "1"),
                        row("S00000002", "EMP-2", "POS-SUP-1", "SKIP", "GBS INDIA", new HcValue(null, true))),
                Instant.parse("2026-08-24T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).isEmpty();
        assertThat(result.kpis()).extracting(SupervisorDeliveryHc::getPl3Code).containsExactly("PL3");
    }

    @Test
    void sumsNumberedCustomerCountrySlicesOntoOneKey() {
        String headers = "month,emp_emp_id,emp_ccgid,emp_name,emp_email,emp_position_id,"
                + "supervisor_emp_id,supervisor_ccgid,supervisor_name,supervisor_position_id,"
                + "sr_manager_emp_id,sr_manager_ccgid,sr_manager_name,sr_manager_position_id,"
                + "domain_head_emp_id,domain_head_ccgid,domain_head_name,domain_head_position_id,"
                + "center,site,gbs_domain,pl1,pl2,pl3_code,pl3,carrier,customer_country,hc,"
                + "management_or_production,cost_type";
        String line = ",EMP-1,S00000001,Agent One,agent@dev.local,EMP-POS-1,"
                + "SUP-1,S00000002,Supervisor One,POS-SUP-1,"
                + "SRM-1,S00000003,Manager One,POS-SRM-1,"
                + "DH-1,S00000004,Head One,POS-DH-1,"
                + "GBS CHINA,CNCKG,Finance,PL1,PL2,PL3,PL3 Name,CEVA,%s,%s,production,productive";
        String csv = headers
                + "\n202610" + line.formatted("MALAYSIA 1", "0.2")
                + "\n202610" + line.formatted("MALAYSIA 3", "0.3")
                + "\n202610" + line.formatted("MALAYSIA", "0.5")
                + "\n202610" + line.formatted("BOLLORE CHINA 1", "0.4")
                + "\n202610" + line.formatted("BOLLORE CHINA 2", "0.6")
                + "\n202610" + line.formatted("CHINA", "1")
                + "\n";
        List<ReportRow> rows = new TimesheetReportParser().parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "monthly.csv");

        TimesheetMonthlyCalculator.Result result = calculator.compute(
                UUID.randomUUID(),
                rows,
                Instant.parse("2026-11-06T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).isEmpty();
        assertThat(result.kpis())
                .extracting(SupervisorDeliveryHc::getCustomerCountry, SupervisorDeliveryHc::getHc)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("MALAYSIA", new BigDecimal("1.000000")),
                        org.assertj.core.groups.Tuple.tuple("BOLLORE CHINA", new BigDecimal("1.000000")),
                        org.assertj.core.groups.Tuple.tuple("CHINA", new BigDecimal("1.000000")));
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String supervisorPositionId,
            String pl3Code,
            String center,
            String hc) {
        return row(empCcgid, empId, supervisorPositionId, pl3Code, center, new HcValue(new BigDecimal(hc), false));
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String supervisorPositionId,
            String pl3Code,
            String center,
            HcValue hc) {
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
        return row(
                empCcgid,
                empId,
                supervisorPositionId,
                pl3Code,
                center,
                new HcValue(new BigDecimal(hc), false),
                managementOrProduction,
                costType);
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String supervisorPositionId,
            String pl3Code,
            String center,
            HcValue hc,
            String managementOrProduction,
            String costType) {
        return new ReportRow(
                2,
                null,
                YearMonth.of(2026, 6).atEndOfMonth(),
                empId,
                empCcgid,
                "Agent",
                (empCcgid == null || empCcgid.isBlank() ? "missing" : empCcgid.toLowerCase()) + "@dev.local",
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
                hc,
                managementOrProduction,
                costType,
                null);
    }
}
