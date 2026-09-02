package com.cmacgm.gbs.rst.api.timesheet.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.ReportRow;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;

/**
 * Shared required-field and filename-date checks for Daily / Monthly rows.
 */
final class TimesheetRowValidator {

    private TimesheetRowValidator() {
    }

    static List<TimesheetSyncIssue> validate(
            UUID runId, String kind, LocalDate expectedDate, List<ReportRow> rows, java.time.Instant now) {
        List<TimesheetSyncIssue> issues = new ArrayList<>();
        for (ReportRow row : rows) {
            if ("DAILY".equals(kind) && !isProductionLine(row)) {
                continue;
            }
            if ("DAILY".equals(kind)) {
                require(issues, runId, now, row, "date", row.date() != null);
                require(issues, runId, now, row, "emp_emp_id", hasText(row.empId()));
                require(issues, runId, now, row, "emp_ccgid", hasText(row.empCcgid()));
                require(issues, runId, now, row, "emp_name", hasText(row.empName()));
                require(issues, runId, now, row, "emp_position_id", hasText(row.empPositionId()));
                require(issues, runId, now, row, "supervisor_emp_id", hasText(row.supervisorId()));
                require(issues, runId, now, row, "supervisor_ccgid", hasText(row.supervisorCcgid()));
                require(issues, runId, now, row, "supervisor_name", hasText(row.supervisorName()));
                require(issues, runId, now, row, "supervisor_position_id", hasText(row.supervisorPositionId()));
                require(issues, runId, now, row, "sr_manager_emp_id", hasText(row.srManagerId()));
                require(issues, runId, now, row, "sr_manager_ccgid", hasText(row.srManagerCcgid()));
                require(issues, runId, now, row, "sr_manager_name", hasText(row.srManagerName()));
                require(issues, runId, now, row, "sr_manager_position_id", hasText(row.srManagerPositionId()));
                require(issues, runId, now, row, "domain_head_emp_id", hasText(row.domainHeadId()));
                require(issues, runId, now, row, "domain_head_ccgid", hasText(row.domainHeadCcgid()));
                require(issues, runId, now, row, "domain_head_name", hasText(row.domainHeadName()));
                require(issues, runId, now, row, "domain_head_position_id", hasText(row.domainHeadPositionId()));
                require(issues, runId, now, row, "center", hasText(row.center()));
                if (expectedDate != null && row.date() != null && !expectedDate.equals(row.date())) {
                    issues.add(mismatch(runId, now, row, expectedDate, row.date()));
                }
            } else {
                LocalDate rowDate = rowDate(row);
                require(issues, runId, now, row, "month", rowDate != null);
                require(issues, runId, now, row, "emp_emp_id", hasText(row.empId()));
                require(issues, runId, now, row, "emp_ccgid", hasText(row.empCcgid()));
                require(issues, runId, now, row, "supervisor_position_id", hasText(row.supervisorPositionId()));
                require(issues, runId, now, row, "pl3_code", hasText(row.pl3Code()));
                require(issues, runId, now, row, "pl3", hasText(row.pl3Name()));
                require(issues, runId, now, row, "center", hasText(row.center()));
                require(issues, runId, now, row, "gbs_domain", hasText(row.domain()));
                require(issues, runId, now, row, "pl1", hasText(row.pl1()));
                require(issues, runId, now, row, "pl2", hasText(row.pl2()));
                require(issues, runId, now, row, "carrier", hasText(row.carrier()));
                require(issues, runId, now, row, "site", hasText(row.site()));
                require(issues, runId, now, row, "customer_country", hasText(row.customerCountry()));
                require(issues, runId, now, row, "hc", row.hc() != null && row.hc().value() != null);
                if (expectedDate != null && rowDate != null && !expectedDate.equals(rowDate)) {
                    issues.add(mismatch(runId, now, row, expectedDate, rowDate));
                }
            }
        }
        return issues;
    }

    /**
     * Daily required-field checks apply only to Production + Productive rows.
     * Monthly required-field checks apply to every row.
     *
     * @param row parsed row
     * @return true when the row is in the validated scope
     */
    static boolean isProductionLine(ReportRow row) {
        return "production".equalsIgnoreCase(nullToEmpty(row.managementOrProduction()))
                && "productive".equalsIgnoreCase(nullToEmpty(row.costType()));
    }

    /**
     * Whether the employee identity on a Daily row can be persisted.
     * Applies to every row, not only Production.
     *
     * @param row parsed row
     * @return true when ccgid, name and position are present
     */
    static boolean isCompleteDaily(ReportRow row) {
        return hasText(row.empCcgid()) && hasText(row.empName()) && hasText(row.empPositionId());
    }

    /**
     * Whether a Monthly row has every required cell. Applies to every
     * management / cost-type combination.
     *
     * @param row parsed row
     * @return true when the row can be persisted
     */
    static boolean isCompleteMonthly(ReportRow row) {
        return rowDate(row) != null
                && hasText(row.empId())
                && hasText(row.empCcgid())
                && hasText(row.supervisorPositionId())
                && hasText(row.pl3Code())
                && hasText(row.pl3Name())
                && hasText(row.center())
                && hasText(row.domain())
                && hasText(row.pl1())
                && hasText(row.pl2())
                && hasText(row.carrier())
                && hasText(row.site())
                && hasText(row.customerCountry())
                && row.hc() != null
                && row.hc().value() != null;
    }

    /**
     * {@code MISSING_FIELD} and {@code ASSIGNMENT_CONFLICT} are recorded but
     * do not fail the run.
     *
     * @param issue persisted or computed issue
     * @return true when the code is advisory
     */
    static boolean isAdvisory(TimesheetSyncIssue issue) {
        String code = issue.getCode();
        return TimesheetSyncErrorCode.MISSING_FIELD.code().equals(code)
                || TimesheetSyncErrorCode.ASSIGNMENT_CONFLICT.code().equals(code);
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

    private static TimesheetSyncIssue mismatch(
            UUID runId, java.time.Instant now, ReportRow row, LocalDate expected, LocalDate actual) {
        return TimesheetSyncIssue.error(
                runId,
                TimesheetSyncErrorCode.DATE_MISMATCH,
                "Date " + actual + " does not match file " + expected + ".",
                row.empId(),
                row.empCcgid(),
                row.empPositionId(),
                row.pl3Code(),
                row.sourceRow(),
                now);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
