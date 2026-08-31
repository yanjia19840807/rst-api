package com.cmacgm.gbs.rst.api.timesheet.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.ReportRow;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetAssignment;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetKpi;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;

/**
 * Builds Monthly assignment, scope and KPI tables from Production +
 * Productive rows.
 */
@Component
public class TimesheetMonthlyCalculator {

    /**
     * Monthly compute result.
     */
    public record Result(
            LocalDate syncDate,
            List<TimesheetScope> scopes,
            List<TimesheetAssignment> assignments,
            List<TimesheetKpi> kpis,
            List<TimesheetSyncIssue> issues) {
    }

    /**
     * Aggregates assignment, scope and Delivery HC for a Monthly run.
     *
     * @param runId Monthly run
     * @param rows parsed rows
     * @param now issue timestamp
     * @return result
     */
    public Result compute(UUID runId, List<ReportRow> rows, Instant now) {
        return compute(runId, rows, now, null);
    }

    /**
     * Aggregates Monthly tables and checks the file-name date.
     *
     * @param runId Monthly run
     * @param rows parsed rows
     * @param now issue timestamp
     * @param expectedDate date from the file name
     * @return result
     */
    public Result compute(UUID runId, List<ReportRow> rows, Instant now, LocalDate expectedDate) {
        List<TimesheetSyncIssue> issues = new ArrayList<>(
                TimesheetRowValidator.validate(runId, "MONTHLY", expectedDate, rows, now));
        Map<String, ScopeDraft> scopes = new LinkedHashMap<>();
        Map<String, AssignmentDraft> assignments = new LinkedHashMap<>();
        Map<String, Set<String>> assignmentSupervisors = new LinkedHashMap<>();
        Map<String, KpiDraft> totals = new LinkedHashMap<>();
        LocalDate syncDate = expectedDate;
        for (ReportRow row : rows) {
            if (syncDate == null) {
                syncDate = TimesheetRowValidator.rowDate(row);
            }
            if (!TimesheetRowValidator.isProductionLine(row)) {
                continue;
            }
            if (hasText(row.supervisorPositionId()) && hasText(row.pl3Code()) && hasText(row.center())
                    && hasText(row.domain()) && hasText(row.pl1()) && hasText(row.pl2())
                    && hasText(row.pl3Name())) {
                scopes.putIfAbsent(
                        key(row.supervisorPositionId(), row.pl3Code(), row.center()),
                        new ScopeDraft(
                                row.supervisorPositionId(),
                                row.pl3Code(),
                                row.center(),
                                row.pl3Name(),
                                row.domain(),
                                row.pl1(),
                                row.pl2()));
            }
            if (hasText(row.empCcgid()) && hasText(row.supervisorPositionId()) && hasText(row.pl3Code())) {
                assignments.putIfAbsent(
                        key(row.empCcgid(), row.supervisorPositionId(), row.pl3Code()),
                        new AssignmentDraft(
                                row.empCcgid(),
                                row.empId(),
                                row.supervisorPositionId(),
                                row.pl3Code()));
                assignmentSupervisors
                        .computeIfAbsent(row.empCcgid(), ignored -> new LinkedHashSet<>())
                        .add(row.supervisorPositionId());
            }
            if (!hasText(row.supervisorPositionId())
                    || !hasText(row.pl3Code())
                    || !hasText(row.carrier())
                    || !hasText(row.site())
                    || !hasText(row.customerCountry())
                    || row.hc() == null
                    || row.hc().value() == null) {
                continue;
            }
            String kpiKey = String.join(
                    "|",
                    row.supervisorPositionId(),
                    row.pl3Code(),
                    row.carrier(),
                    row.site(),
                    row.customerCountry());
            totals.merge(
                    kpiKey,
                    new KpiDraft(
                            row.supervisorPositionId(),
                            row.pl3Code(),
                            row.carrier(),
                            row.site(),
                            row.customerCountry(),
                            row.hc().value()),
                    (left, right) -> new KpiDraft(
                            left.supervisorPositionId,
                            left.pl3Code,
                            left.carrier,
                            left.site,
                            left.customerCountry,
                            left.hc.add(right.hc)));
        }
        for (Map.Entry<String, Set<String>> entry : assignmentSupervisors.entrySet()) {
            if (entry.getValue().size() > 1) {
                issues.add(TimesheetSyncIssue.error(
                        runId,
                        TimesheetSyncErrorCode.ASSIGNMENT_CONFLICT,
                        "emp_ccgid maps to multiple supervisor_position_id: "
                                + String.join(", ", entry.getValue()),
                        null,
                        entry.getKey(),
                        null,
                        null,
                        null,
                        now));
            }
        }
        if (syncDate == null) {
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    TimesheetSyncErrorCode.INVALID_MONTH,
                    "Monthly file has no valid date or month.",
                    null,
                    null,
                    null,
                    null,
                    null,
                    now));
        }
        if (scopes.isEmpty() && assignments.isEmpty() && totals.isEmpty() && issues.isEmpty()) {
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    TimesheetSyncErrorCode.EMPTY_FILE,
                    "Monthly file produced no assignment, scope or KPI rows.",
                    null,
                    null,
                    null,
                    null,
                    null,
                    now));
        }
        return new Result(
                syncDate,
                scopes.values().stream()
                        .map(draft -> TimesheetScope.create(
                                runId,
                                draft.supervisorPositionId,
                                draft.pl3Code,
                                draft.center,
                                draft.pl3Name,
                                draft.domain,
                                draft.pl1,
                                draft.pl2))
                        .toList(),
                assignments.values().stream()
                        .map(draft -> TimesheetAssignment.create(
                                runId, draft.empCcgid, draft.empId, draft.supervisorPositionId, draft.pl3Code))
                        .toList(),
                totals.values().stream()
                        .map(draft -> TimesheetKpi.create(
                                runId,
                                draft.supervisorPositionId,
                                draft.pl3Code,
                                draft.carrier,
                                draft.site,
                                draft.customerCountry,
                                draft.hc))
                        .toList(),
                issues);
    }

    private static String key(String... parts) {
        return String.join("|", parts);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ScopeDraft(
            String supervisorPositionId,
            String pl3Code,
            String center,
            String pl3Name,
            String domain,
            String pl1,
            String pl2) {
    }

    private record AssignmentDraft(
            String empCcgid, String empId, String supervisorPositionId, String pl3Code) {
    }

    private record KpiDraft(
            String supervisorPositionId,
            String pl3Code,
            String carrier,
            String site,
            String customerCountry,
            BigDecimal hc) {
    }
}
