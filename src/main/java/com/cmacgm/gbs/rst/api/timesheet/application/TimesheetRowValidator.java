package com.cmacgm.gbs.rst.api.timesheet.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.ReportRow;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;

/**
 * Shared row checks for Daily / Monthly.
 *
 * <p>Monthly checks RST-applicable rows only: required cells and typed
 * {@code hc}. Date and Center come from the file name.
 *
 * <p>Daily person fields are checked on every row before people are
 * collected. Supervisor / Sr Manager fields, unique parent chain and
 * one-person-one-seat apply before positions are built (RST-applicable
 * Production + Productive). Daily date and Center come from the file
 * name. Daily never validates {@code hc}.
 *
 * <p>Person identity {@code MISSING_FIELD} and Monthly {@code INVALID_HC}
 * fail the run. Daily empty supervisor / Sr Manager cells and
 * {@code HIERARCHY_CONFLICT} stay advisory. Daily does not treat one
 * person on two seats as a conflict. Historical {@code ASSIGNMENT_CONFLICT}
 * stays advisory.
 */
final class TimesheetRowValidator {

    private static final Set<String> DAILY_ADVISORY_MISSING_FIELDS = Set.of(
            "supervisor_emp_id",
            "supervisor_ccgid",
            "supervisor_name",
            "supervisor_position_id",
            "sr_manager_emp_id",
            "sr_manager_ccgid",
            "sr_manager_name",
            "sr_manager_position_id");

    private TimesheetRowValidator() {
    }

    static List<TimesheetSyncIssue> validate(
            UUID runId, String kind, LocalDate expectedDate, List<ReportRow> rows, java.time.Instant now) {
        return validate(runId, kind, expectedDate, rows, now, GbsProcessCatalog.allowing());
    }

    /**
     * Validates RST-in-scope rows. Out-of-scope rows are skipped.
     *
     * @param runId sync run
     * @param kind DAILY or MONTHLY
     * @param expectedDate file-name date
     * @param rows parsed rows
     * @param now issue timestamp
     * @param catalog RST-applicable PL3 codes
     * @return issues
     */
    static List<TimesheetSyncIssue> validate(
            UUID runId,
            String kind,
            LocalDate expectedDate,
            List<ReportRow> rows,
            java.time.Instant now,
            GbsProcessCatalog catalog) {
        GbsProcessCatalog processes = catalog == null ? GbsProcessCatalog.allowing() : catalog;
        List<TimesheetSyncIssue> issues = new ArrayList<>();
        for (ReportRow row : rows) {
            if ("DAILY".equals(kind)) {
                issues.addAll(validateDailyPeople(runId, List.of(row), now));
                issues.addAll(validateDailyPositions(runId, List.of(row), now, processes));
                continue;
            }
            if (!inScope(kind, row, processes)) {
                continue;
            }
            require(issues, runId, now, row, "emp_emp_id", hasText(row.empId()));
            require(issues, runId, now, row, "emp_ccgid", hasText(row.empCcgid()));
            require(issues, runId, now, row, "supervisor_position_id", hasText(row.supervisorPositionId()));
            require(issues, runId, now, row, "pl3_code", hasText(row.pl3Code()));
            require(issues, runId, now, row, "pl3", hasText(row.pl3Name()));
            require(issues, runId, now, row, "gbs_domain", hasText(row.domain()));
            require(issues, runId, now, row, "pl1", hasText(row.pl1()));
            require(issues, runId, now, row, "pl2", hasText(row.pl2()));
            require(issues, runId, now, row, "carrier", hasText(row.carrier()));
            require(issues, runId, now, row, "site", hasText(row.site()));
            require(issues, runId, now, row, "customer_country", hasText(row.customerCountry()));
            requireHc(issues, runId, now, row);
        }
        return issues;
    }

    /**
     * Required person cells on every Daily row. Date and Center come from
     * the file name.
     *
     * @param runId sync run
     * @param rows parsed rows
     * @param now issue timestamp
     * @return person-field issues
     */
    static List<TimesheetSyncIssue> validateDailyPeople(UUID runId, List<ReportRow> rows, java.time.Instant now) {
        List<TimesheetSyncIssue> issues = new ArrayList<>();
        for (ReportRow row : rows) {
            requireDailyPerson(issues, runId, now, row);
        }
        return issues;
    }

    /**
     * Required hierarchy cells on Daily position rows. Date and Center
     * come from the file name.
     *
     * @param runId sync run
     * @param rows parsed rows
     * @param now issue timestamp
     * @param catalog RST-applicable PL3 codes
     * @return position-field issues
     */
    static List<TimesheetSyncIssue> validateDailyPositions(
            UUID runId, List<ReportRow> rows, java.time.Instant now, GbsProcessCatalog catalog) {
        List<TimesheetSyncIssue> issues = new ArrayList<>();
        for (ReportRow row : rows) {
            if (!inScope("DAILY", row, catalog)) {
                continue;
            }
            requireDailyHierarchy(issues, runId, now, row);
        }
        return issues;
    }

    /**
     * Whether this row is used for Daily positions / Monthly scope.
     * Daily person fields are checked even when this is false.
     *
     * @param kind DAILY or MONTHLY
     * @param row parsed row
     * @param catalog RST-applicable PL3 codes
     * @return true when the row is kept for RST validation
     */
    static boolean inScope(String kind, ReportRow row, GbsProcessCatalog catalog) {
        GbsProcessCatalog processes = catalog == null ? GbsProcessCatalog.allowing() : catalog;
        if (!processes.applies(row.pl3Code())) {
            return false;
        }
        return !"DAILY".equals(kind) || isProductionLine(row);
    }

    /**
     * Daily position mapping uses Production + Productive rows.
     *
     * @param row parsed row
     * @return true when the row is Production and Productive
     */
    static boolean isProductionLine(ReportRow row) {
        return "production".equalsIgnoreCase(nullToEmpty(row.managementOrProduction()))
                && "productive".equalsIgnoreCase(nullToEmpty(row.costType()));
    }

    /**
     * Whether the employee identity on a Daily row can be persisted.
     *
     * @param row parsed row
     * @return true when ccgid, name and position are present
     */
    static boolean isCompleteDaily(ReportRow row) {
        return hasText(row.empCcgid()) && hasText(row.empName()) && hasText(row.empPositionId());
    }

    private static void requireDailyPerson(
            List<TimesheetSyncIssue> issues, UUID runId, java.time.Instant now, ReportRow row) {
        require(issues, runId, now, row, "emp_emp_id", hasText(row.empId()));
        require(issues, runId, now, row, "emp_ccgid", hasText(row.empCcgid()));
        require(issues, runId, now, row, "emp_name", hasText(row.empName()));
        require(issues, runId, now, row, "emp_position_id", hasText(row.empPositionId()));
    }

    private static void requireDailyHierarchy(
            List<TimesheetSyncIssue> issues, UUID runId, java.time.Instant now, ReportRow row) {
        require(issues, runId, now, row, "supervisor_emp_id", hasText(row.supervisorId()));
        require(issues, runId, now, row, "supervisor_ccgid", hasText(row.supervisorCcgid()));
        require(issues, runId, now, row, "supervisor_name", hasText(row.supervisorName()));
        require(issues, runId, now, row, "supervisor_position_id", hasText(row.supervisorPositionId()));
        require(issues, runId, now, row, "sr_manager_emp_id", hasText(row.srManagerId()));
        require(issues, runId, now, row, "sr_manager_ccgid", hasText(row.srManagerCcgid()));
        require(issues, runId, now, row, "sr_manager_name", hasText(row.srManagerName()));
        require(issues, runId, now, row, "sr_manager_position_id", hasText(row.srManagerPositionId()));
    }

    /**
     * Whether a Monthly row has every required cell. Date and Center come
     * from the file name. Applies to every management / cost-type
     * combination.
     *
     * @param row parsed row
     * @return true when the row can be persisted
     */
    static boolean isCompleteMonthly(ReportRow row) {
        return hasText(row.empId())
                && hasText(row.empCcgid())
                && hasText(row.supervisorPositionId())
                && hasText(row.pl3Code())
                && hasText(row.pl3Name())
                && hasText(row.domain())
                && hasText(row.pl1())
                && hasText(row.pl2())
                && hasText(row.carrier())
                && hasText(row.site())
                && hasText(row.customerCountry())
                && hasUsableHc(row);
    }

    /**
     * Whether this issue should be stored without failing the run.
     * Historical {@code ASSIGNMENT_CONFLICT} stays advisory. Daily empty
     * hierarchy cells and dual parents are advisory; person identity
     * {@code MISSING_FIELD} still fails the run.
     *
     * @param issue persisted or computed issue
     * @return true when the code is advisory for Daily
     */
    static boolean isAdvisory(TimesheetSyncIssue issue) {
        return isAdvisory(issue, "DAILY");
    }

    /**
     * Whether this issue should be stored without failing the run.
     *
     * @param issue persisted or computed issue
     * @param kind DAILY or MONTHLY
     * @return true when the code is advisory for this kind
     */
    static boolean isAdvisory(TimesheetSyncIssue issue, String kind) {
        String code = issue.getCode();
        if (TimesheetSyncErrorCode.ASSIGNMENT_CONFLICT.code().equals(code)) {
            return true;
        }
        if (!"DAILY".equals(kind)) {
            return false;
        }
        if (TimesheetSyncErrorCode.HIERARCHY_CONFLICT.code().equals(code)) {
            return true;
        }
        return TimesheetSyncErrorCode.MISSING_FIELD.code().equals(code)
                && isDailyHierarchyMissingField(issue.getMessage());
    }

    private static boolean isDailyHierarchyMissingField(String message) {
        if (message == null || !message.startsWith("Missing ") || !message.endsWith(".")) {
            return false;
        }
        String field = message.substring("Missing ".length(), message.length() - 1);
        return DAILY_ADVISORY_MISSING_FIELDS.contains(field);
    }

    static LocalDate rowDate(ReportRow row) {
        if (row.date() != null) {
            return row.date();
        }
        if (row.month() != null) {
            return row.month().withDayOfMonth(row.month().lengthOfMonth());
        }
        return null;
    }

    private static void requireHc(
            List<TimesheetSyncIssue> issues, UUID runId, java.time.Instant now, ReportRow row) {
        if (row.hc() != null && row.hc().invalid()) {
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    TimesheetSyncErrorCode.INVALID_HC,
                    "hc is not a valid number.",
                    row.empId(),
                    row.empCcgid(),
                    row.empPositionId(),
                    row.pl3Code(),
                    row.sourceRow(),
                    now));
            return;
        }
        require(issues, runId, now, row, "hc", hasUsableHc(row));
    }

    private static boolean hasUsableHc(ReportRow row) {
        return row.hc() != null && !row.hc().invalid() && row.hc().value() != null;
    }

    private static void require(
            List<TimesheetSyncIssue> issues,
            UUID runId,
            java.time.Instant now,
            ReportRow row,
            String field,
            boolean ok) {
        if (ok) {
            return;
        }
        issues.add(TimesheetSyncIssue.error(
                runId,
                TimesheetSyncErrorCode.MISSING_FIELD,
                "Missing " + field + ".",
                row.empId(),
                row.empCcgid(),
                row.empPositionId(),
                row.pl3Code(),
                row.sourceRow(),
                now));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
