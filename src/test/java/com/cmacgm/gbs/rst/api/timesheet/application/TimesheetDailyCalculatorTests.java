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
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPosition;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;
import org.junit.jupiter.api.Test;

class TimesheetDailyCalculatorTests {

    private static final GbsProcessCatalog RST_YES = GbsProcessCatalog.allowing("PL3");

    private final TimesheetDailyCalculator calculator = new TimesheetDailyCalculator();

    @Test
    void personComesFromDistinctEmpColumns() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000001", "EMP-1", "Agent One", "EMP-POS-1", "production", "productive")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).isEmpty();
        assertThat(result.people())
                .extracting(
                        TimesheetPerson::getCcgid,
                        TimesheetPerson::getCenter,
                        TimesheetPerson::getEmail,
                        TimesheetPerson::getPositionId)
                .contains(org.assertj.core.groups.Tuple.tuple(
                        "S00000001", "GBS INDIA", "s00000001@dev.local", "EMP-POS-1"));
        assertThat(result.people())
                .extracting(TimesheetPerson::getCcgid, TimesheetPerson::getPositionId)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("S00000002", "POS-SUP-1"),
                        org.assertj.core.groups.Tuple.tuple("S00000003", "POS-SRM-1"),
                        org.assertj.core.groups.Tuple.tuple("S00000004", "POS-DH-1"));
        assertThat(result.positions())
                .extracting(TimesheetPosition::getPositionId, TimesheetPosition::getRoleType)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("EMP-POS-1", "AGENT"),
                        org.assertj.core.groups.Tuple.tuple("POS-SUP-1", "SUPERVISOR"),
                        org.assertj.core.groups.Tuple.tuple("POS-SRM-1", "SR_MANAGER"),
                        org.assertj.core.groups.Tuple.tuple("POS-DH-1", "DOMAIN_HEAD"));
    }

    @Test
    void persistsEmpJobRoleOnTheEmployee() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(row(
                        "S00000001",
                        "EMP-1",
                        "Agent One",
                        "EMP-POS-1",
                        "production",
                        "productive",
                        "SRM-1",
                        "GBS INDIA",
                        "POS-SUP-1",
                        "Billing Clerk")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).isEmpty();
        assertThat(result.people())
                .filteredOn(person -> "S00000001".equals(person.getCcgid()))
                .extracting(TimesheetPerson::getJobRole)
                .containsExactly("Billing Clerk");
        assertThat(result.people())
                .filteredOn(person -> !"S00000001".equals(person.getCcgid()))
                .extracting(TimesheetPerson::getJobRole)
                .containsOnly((String) null);
    }

    @Test
    void skipsRowsWhenPl3IsNotRstApplicable() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000002", "SUP-1", "Supervisor One", "POS-SUP-1", "production", "productive")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                GbsProcessCatalog.allowing("OTHER"));

        assertThat(result.people())
                .extracting(TimesheetPerson::getCcgid, TimesheetPerson::getPositionId)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("S00000002", "POS-SUP-1"),
                        org.assertj.core.groups.Tuple.tuple("S00000003", "POS-SRM-1"),
                        org.assertj.core.groups.Tuple.tuple("S00000004", "POS-DH-1"));
        assertThat(result.positions()).isEmpty();
        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("EMPTY_FILE");
    }

    @Test
    void skipsPositionsWhenRstApplicableButNotProductionLine() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000001", "EMP-1", "Agent One", "EMP-POS-1", "management", "non-productive")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.people())
                .extracting(TimesheetPerson::getCcgid, TimesheetPerson::getPositionId)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("S00000001", "EMP-POS-1"),
                        org.assertj.core.groups.Tuple.tuple("S00000002", "POS-SUP-1"),
                        org.assertj.core.groups.Tuple.tuple("S00000003", "POS-SRM-1"),
                        org.assertj.core.groups.Tuple.tuple("S00000004", "POS-DH-1"));
        assertThat(result.positions()).isEmpty();
        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("MISSING_FIELD");
    }

    @Test
    void doesNotRequireHierarchyWhenPl3IsNotRstApplicable() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000002", "SUP-1", "Supervisor One", "POS-SUP-1", "production", "productive", "")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                GbsProcessCatalog.allowing("OTHER"));

        assertThat(result.people()).extracting(TimesheetPerson::getCcgid).contains("S00000002");
        assertThat(result.positions()).isEmpty();
        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("MISSING_FIELD");
    }

    @Test
    void requiresHierarchyOnRstApplicableLines() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000001", "EMP-1", "Agent One", "EMP-POS-1", "production", "productive", "")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues())
                .extracting(TimesheetSyncIssue::getCode, TimesheetSyncIssue::getMessage)
                .contains(org.assertj.core.groups.Tuple.tuple("MISSING_FIELD", "Missing sr_manager_emp_id."));
        assertThat(result.issues()).allMatch(issue -> !TimesheetRowValidator.isAdvisory(issue, "DAILY"));
        assertThat(result.issues())
                .extracting(TimesheetSyncIssue::getMessage)
                .noneMatch(message -> message.contains("domain_head"));
        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("EMPTY_FILE");
        assertThat(result.people()).extracting(TimesheetPerson::getCcgid).contains("S00000001");
        assertThat(result.positions()).isEmpty();
    }

    @Test
    void ignoresDomainHeadColumnsWhenBuildingPositions() {
        UUID runId = UUID.randomUUID();
        ReportRow withoutDomainHead = new ReportRow(
                2,
                LocalDate.of(2026, 7, 27),
                null,
                "EMP-1",
                "S00000001",
                "Agent One",
                "s00000001@dev.local",
                "EMP-POS-1",
                "SUP-1",
                "S00000002",
                "Supervisor One",
                "POS-SUP-1",
                "SRM-1",
                "S00000003",
                "Manager One",
                "POS-SRM-1",
                "",
                "",
                "",
                "",
                "GBS INDIA",
                "Site",
                "Finance",
                "PL1",
                "PL2",
                "PL3",
                "PL3 Name",
                "CMA",
                "MY",
                new HcValue(BigDecimal.ONE, false),
                "production",
                "productive",
                null);

        TimesheetDailyCalculator.Result result = calculator.compute(
                runId, List.of(withoutDomainHead), Instant.parse("2026-08-23T00:00:00Z"), null, RST_YES);

        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("MISSING_FIELD");
        assertThat(result.people()).extracting(TimesheetPerson::getCcgid).doesNotContain("S00000004");
        assertThat(result.positions())
                .extracting(TimesheetPosition::getPositionId, TimesheetPosition::getRoleType)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("EMP-POS-1", "AGENT"),
                        org.assertj.core.groups.Tuple.tuple("POS-SUP-1", "SUPERVISOR"),
                        org.assertj.core.groups.Tuple.tuple("POS-SRM-1", "SR_MANAGER"))
                .doesNotContain(org.assertj.core.groups.Tuple.tuple("POS-DH-1", "DOMAIN_HEAD"));
        assertThat(result.positions())
                .filteredOn(position -> "POS-SRM-1".equals(position.getPositionId()))
                .extracting(TimesheetPosition::getParentPositionId)
                .containsExactly((String) null);
    }

    @Test
    void ignoresHcOnDailyRows() {
        UUID runId = UUID.randomUUID();
        ReportRow withInvalidHc = row("S00000001", "EMP-1", "Agent One", "EMP-POS-1", "production", "productive");
        withInvalidHc = new ReportRow(
                withInvalidHc.sourceRow(),
                withInvalidHc.date(),
                withInvalidHc.month(),
                withInvalidHc.empId(),
                withInvalidHc.empCcgid(),
                withInvalidHc.empName(),
                withInvalidHc.empEmail(),
                withInvalidHc.empPositionId(),
                withInvalidHc.supervisorId(),
                withInvalidHc.supervisorCcgid(),
                withInvalidHc.supervisorName(),
                withInvalidHc.supervisorPositionId(),
                withInvalidHc.srManagerId(),
                withInvalidHc.srManagerCcgid(),
                withInvalidHc.srManagerName(),
                withInvalidHc.srManagerPositionId(),
                withInvalidHc.domainHeadId(),
                withInvalidHc.domainHeadCcgid(),
                withInvalidHc.domainHeadName(),
                withInvalidHc.domainHeadPositionId(),
                withInvalidHc.center(),
                withInvalidHc.site(),
                withInvalidHc.domain(),
                withInvalidHc.pl1(),
                withInvalidHc.pl2(),
                withInvalidHc.pl3Code(),
                withInvalidHc.pl3Name(),
                withInvalidHc.carrier(),
                withInvalidHc.customerCountry(),
                new HcValue(null, true),
                withInvalidHc.managementOrProduction(),
                withInvalidHc.costType(),
                withInvalidHc.empJobRole());

        TimesheetDailyCalculator.Result result = calculator.compute(
                runId, List.of(withInvalidHc), Instant.parse("2026-08-23T00:00:00Z"), null, RST_YES);

        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("INVALID_HC", "MISSING_FIELD");
        assertThat(result.people()).extracting(TimesheetPerson::getCcgid).contains("S00000001");
        assertThat(result.positions()).isNotEmpty();
    }

    @Test
    void skipsPositionsWhenHierarchyIsIncomplete() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "Agent One", "EMP-POS-1", "production", "productive"),
                        row("S00000006", "EMP-6", "Agent Six", "EMP-POS-6", "production", "productive", "")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).contains("MISSING_FIELD");
        assertThat(result.issues()).allMatch(issue -> !TimesheetRowValidator.isAdvisory(issue, "DAILY"));
        assertThat(result.people())
                .extracting(TimesheetPerson::getCcgid)
                .contains("S00000001", "S00000006");
        assertThat(result.positions()).isEmpty();
    }

    @Test
    void usesFilenameCenterInsteadOfRowCenter() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(row(
                        "S00000001",
                        "EMP-1",
                        "Agent One",
                        "EMP-POS-1",
                        "production",
                        "productive",
                        "SRM-1",
                        "GBS VIETNAM")),
                Instant.parse("2026-08-23T00:00:00Z"),
                LocalDate.of(2026, 7, 27),
                RST_YES,
                "GBS INDIA");

        assertThat(result.issues()).isEmpty();
        assertThat(result.syncDate()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(result.people())
                .filteredOn(person -> "S00000001".equals(person.getCcgid()))
                .extracting(TimesheetPerson::getCenter)
                .containsExactly("GBS INDIA");
        assertThat(result.positions()).allMatch(position -> "GBS INDIA".equals(position.getCenter()));
    }

    @Test
    void skipsUnknownCenterWhenPl3IsNotRstApplicable() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(row(
                        "S00000001",
                        "EMP-1",
                        "Agent One",
                        "EMP-POS-1",
                        "production",
                        "productive",
                        "SRM-1",
                        "GBS VIETNAM")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                GbsProcessCatalog.allowing("OTHER"));

        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("UNKNOWN_CENTER");
        assertThat(result.positions()).isEmpty();
    }

    @Test
    void ignoresRowDateAndUsesFilenameDate() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000001", "EMP-1", "Agent One", "EMP-POS-1", "production", "productive")),
                Instant.parse("2026-08-23T00:00:00Z"),
                LocalDate.of(2026, 7, 26),
                RST_YES);

        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("DATE_MISMATCH");
        assertThat(result.syncDate()).isEqualTo(LocalDate.of(2026, 7, 26));
        assertThat(result.people()).isNotEmpty();
        assertThat(result.positions()).isNotEmpty();
    }

    @Test
    void samePersonOnTwoEmpPositionsIsAConflict() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "Same Person", "EMP-POS-1", "production", "productive"),
                        row("S00000001", "EMP-1", "Same Person", "EMP-POS-2", "production", "productive")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues())
                .extracting(TimesheetSyncIssue::getCode)
                .contains("PERSON_POSITION_CONFLICT");
        assertThat(result.positions()).isEmpty();
    }

    @Test
    void allowsTwoPeopleOnTheSamePosition() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "Agent One", "EMP-POS-1", "production", "productive"),
                        row("S00000005", "EMP-5", "Agent Five", "EMP-POS-1", "management", "non-productive")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("OCCUPANCY_CONFLICT");
        assertThat(result.people())
                .extracting(TimesheetPerson::getCcgid, TimesheetPerson::getPositionId)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("S00000001", "EMP-POS-1"),
                        org.assertj.core.groups.Tuple.tuple("S00000005", "EMP-POS-1"));
    }

    @Test
    void ignoresSecondSeatWhenItIsNotProductionProductive() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row("S00000001", "EMP-1", "Same Person", "EMP-POS-1", "production", "productive"),
                        row("S00000001", "EMP-1", "Same Person", "EMP-POS-2", "management", "non-productive")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("PERSON_POSITION_CONFLICT");
        assertThat(result.people())
                .extracting(TimesheetPerson::getCcgid, TimesheetPerson::getPositionId)
                .contains(org.assertj.core.groups.Tuple.tuple("S00000001", "EMP-POS-1"));
        assertThat(result.positions())
                .extracting(TimesheetPosition::getPositionId)
                .contains("EMP-POS-1")
                .doesNotContain("EMP-POS-2");
    }

    @Test
    void flagsHierarchyConflictOnProductionRowsOnly() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        productionRow("S00000001", "EMP-1", "Agent One", "EMP-POS-1", "POS-SUP-1"),
                        productionRow("S00000005", "EMP-5", "Agent Five", "EMP-POS-1", "POS-SUP-2")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues())
                .extracting(TimesheetSyncIssue::getCode, TimesheetSyncIssue::getMessage)
                .contains(org.assertj.core.groups.Tuple.tuple(
                        "HIERARCHY_CONFLICT",
                        "position_id EMP-POS-1 maps to multiple parent_position_id: POS-SUP-1, POS-SUP-2"));
        assertThat(result.positions()).isEmpty();
    }

    @Test
    void ignoresHierarchyConflictOnNonProductionRows() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        productionRow("S00000001", "EMP-1", "Agent One", "EMP-POS-1", "POS-SUP-1"),
                        managementRow("S00000005", "EMP-5", "Agent Five", "EMP-POS-1", "POS-SUP-2")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("HIERARCHY_CONFLICT");
        assertThat(result.positions())
                .filteredOn(position -> "EMP-POS-1".equals(position.getPositionId()))
                .extracting(TimesheetPosition::getParentPositionId)
                .containsExactly("POS-SUP-1");
    }

    @Test
    void samePersonOnTwoCentersIsNotAConflict() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(
                        row(
                                "S00000001",
                                "EMP-1",
                                "Same Person",
                                "EMP-POS-1",
                                "production",
                                "productive",
                                "SRM-1",
                                "GBS CHINA"),
                        row(
                                "S00000001",
                                "EMP-1",
                                "Same Person",
                                "EMP-POS-1",
                                "production",
                                "productive",
                                "SRM-1",
                                "GBS INDIA")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.issues()).isEmpty();
        assertThat(result.people()).extracting(TimesheetPerson::getCcgid).contains("S00000001");
    }

    @Test
    void collectsHierarchyPeopleFromNonProductionRowsWithoutPositions() {
        UUID runId = UUID.randomUUID();
        TimesheetDailyCalculator.Result result = calculator.compute(
                runId,
                List.of(row("S00000001", "EMP-1", "Agent One", "EMP-POS-1", "management", "non-productive")),
                Instant.parse("2026-08-23T00:00:00Z"),
                null,
                RST_YES);

        assertThat(result.people())
                .extracting(TimesheetPerson::getCcgid, TimesheetPerson::getPositionId)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("S00000001", "EMP-POS-1"),
                        org.assertj.core.groups.Tuple.tuple("S00000002", "POS-SUP-1"),
                        org.assertj.core.groups.Tuple.tuple("S00000003", "POS-SRM-1"),
                        org.assertj.core.groups.Tuple.tuple("S00000004", "POS-DH-1"));
        assertThat(result.positions()).isEmpty();
        assertThat(result.issues()).extracting(TimesheetSyncIssue::getCode).doesNotContain("MISSING_FIELD");
    }

    @Test
    void requiresPersonFieldsOnNonProductionRows() {
        UUID runId = UUID.randomUUID();
        ReportRow missingEmp = row("S00000001", "EMP-1", "Agent One", "EMP-POS-1", "management", "non-productive");
        missingEmp = new ReportRow(
                missingEmp.sourceRow(),
                missingEmp.date(),
                missingEmp.month(),
                missingEmp.empId(),
                "",
                missingEmp.empName(),
                missingEmp.empEmail(),
                missingEmp.empPositionId(),
                missingEmp.supervisorId(),
                missingEmp.supervisorCcgid(),
                missingEmp.supervisorName(),
                missingEmp.supervisorPositionId(),
                missingEmp.srManagerId(),
                missingEmp.srManagerCcgid(),
                missingEmp.srManagerName(),
                missingEmp.srManagerPositionId(),
                missingEmp.domainHeadId(),
                missingEmp.domainHeadCcgid(),
                missingEmp.domainHeadName(),
                missingEmp.domainHeadPositionId(),
                missingEmp.center(),
                missingEmp.site(),
                missingEmp.domain(),
                missingEmp.pl1(),
                missingEmp.pl2(),
                missingEmp.pl3Code(),
                missingEmp.pl3Name(),
                missingEmp.carrier(),
                missingEmp.customerCountry(),
                missingEmp.hc(),
                missingEmp.managementOrProduction(),
                missingEmp.costType(),
                missingEmp.empJobRole());

        TimesheetDailyCalculator.Result result = calculator.compute(
                runId, List.of(missingEmp), Instant.parse("2026-08-23T00:00:00Z"), null, RST_YES);

        assertThat(result.issues())
                .extracting(TimesheetSyncIssue::getCode, TimesheetSyncIssue::getMessage)
                .contains(org.assertj.core.groups.Tuple.tuple("MISSING_FIELD", "Missing emp_ccgid."));
        assertThat(result.issues())
                .extracting(TimesheetSyncIssue::getMessage)
                .noneMatch(message -> message.contains("supervisor") || message.contains("sr_manager"));
        assertThat(result.people()).isEmpty();
        assertThat(result.positions()).isEmpty();
    }

    @Test
    void fillsSupervisorFromProductionColumnsWhenMissingOwnRow() {
        UUID runId = UUID.randomUUID();
        ReportRow agent = new ReportRow(
                2,
                LocalDate.of(2026, 7, 27),
                null,
                "EMP-1",
                "S00000001",
                "Agent One",
                "s00000001@dev.local",
                "EMP-POS-1",
                "SUP-DONNA",
                "S01031707",
                "DONG Donna",
                "273656",
                "SRM-1",
                "S00000003",
                "Manager One",
                "POS-SRM-1",
                "DH-1",
                "S00000004",
                "Head One",
                "POS-DH-1",
                "GBS INDIA",
                "Site",
                "Finance",
                "PL1",
                "PL2",
                "PL3",
                "PL3 Name",
                "CMA",
                "MY",
                new HcValue(BigDecimal.ONE, false),
                "production",
                "productive",
                null);

        TimesheetDailyCalculator.Result result = calculator.compute(
                runId, List.of(agent), Instant.parse("2026-08-23T00:00:00Z"), null, RST_YES);

        assertThat(result.people())
                .extracting(TimesheetPerson::getCcgid, TimesheetPerson::getPositionId)
                .contains(org.assertj.core.groups.Tuple.tuple("S01031707", "273656"));
        assertThat(result.positions())
                .extracting(TimesheetPosition::getPositionId, TimesheetPosition::getRoleType)
                .contains(org.assertj.core.groups.Tuple.tuple("273656", "SUPERVISOR"));
    }

    private static ReportRow productionRow(
            String empCcgid, String empId, String empName, String empPositionId, String supervisorPositionId) {
        return row(
                empCcgid,
                empId,
                empName,
                empPositionId,
                "production",
                "productive",
                "SRM-1",
                "GBS INDIA",
                supervisorPositionId);
    }

    private static ReportRow managementRow(
            String empCcgid, String empId, String empName, String empPositionId, String supervisorPositionId) {
        return row(
                empCcgid,
                empId,
                empName,
                empPositionId,
                "management",
                "non-productive",
                "SRM-1",
                "GBS INDIA",
                supervisorPositionId);
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String empName,
            String empPositionId,
            String managementOrProduction,
            String costType) {
        return row(empCcgid, empId, empName, empPositionId, managementOrProduction, costType, "SRM-1", "GBS INDIA");
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String empName,
            String empPositionId,
            String managementOrProduction,
            String costType,
            String srManagerId) {
        return row(
                empCcgid,
                empId,
                empName,
                empPositionId,
                managementOrProduction,
                costType,
                srManagerId,
                "GBS INDIA");
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String empName,
            String empPositionId,
            String managementOrProduction,
            String costType,
            String srManagerId,
            String center) {
        return row(
                empCcgid,
                empId,
                empName,
                empPositionId,
                managementOrProduction,
                costType,
                srManagerId,
                center,
                "POS-SUP-1");
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String empName,
            String empPositionId,
            String managementOrProduction,
            String costType,
            String srManagerId,
            String center,
            String supervisorPositionId) {
        return row(
                empCcgid,
                empId,
                empName,
                empPositionId,
                managementOrProduction,
                costType,
                srManagerId,
                center,
                supervisorPositionId,
                null);
    }

    private static ReportRow row(
            String empCcgid,
            String empId,
            String empName,
            String empPositionId,
            String managementOrProduction,
            String costType,
            String srManagerId,
            String center,
            String supervisorPositionId,
            String empJobRole) {
        return new ReportRow(
                2,
                LocalDate.of(2026, 7, 27),
                null,
                empId,
                empCcgid,
                empName,
                empCcgid.toLowerCase() + "@dev.local",
                empPositionId,
                "SUP-1",
                "S00000002",
                "Supervisor One",
                supervisorPositionId,
                srManagerId,
                "S00000003",
                "Manager One",
                "POS-SRM-1",
                "DH-1",
                "S00000004",
                "Head One",
                "POS-DH-1",
                center,
                "Site",
                "Finance",
                "PL1",
                "PL2",
                "PL3",
                "PL3 Name",
                "CMA",
                "MY",
                new HcValue(BigDecimal.ONE, false),
                managementOrProduction,
                costType,
                empJobRole);
    }
}
