package com.cmacgm.gbs.rst.api.timesheet.application;

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
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPosition;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;

/**
 * Builds Daily person and position tables from distinct employee rows
 * and production report lines.
 */
@Component
public class TimesheetDailyCalculator {

    /**
     * Daily compute result.
     */
    public record Result(
            LocalDate syncDate,
            List<TimesheetPerson> people,
            List<TimesheetPosition> positions,
            List<TimesheetSyncIssue> issues) {
    }

    /**
     * Computes Daily org tables for a run.
     *
     * @param runId Daily run
     * @param rows parsed rows
     * @param now issue timestamp
     * @return result
     */
    public Result compute(UUID runId, List<ReportRow> rows, Instant now) {
        return compute(runId, rows, now, null);
    }

    /**
     * Computes Daily org tables and checks the file-name date.
     *
     * @param runId Daily run
     * @param rows parsed rows
     * @param now issue timestamp
     * @param expectedDate date from the file name
     * @return result
     */
    public Result compute(UUID runId, List<ReportRow> rows, Instant now, LocalDate expectedDate) {
        List<TimesheetSyncIssue> issues = new ArrayList<>(
                TimesheetRowValidator.validate(runId, "DAILY", expectedDate, rows, now));
        Map<String, PersonDraft> people = new LinkedHashMap<>();
        Map<String, Set<String>> personToPosition = new LinkedHashMap<>();
        Map<String, Set<String>> positionToPerson = new LinkedHashMap<>();
        Map<String, Set<String>> personToCenter = new LinkedHashMap<>();
        Map<String, PositionDraft> positions = new LinkedHashMap<>();
        Map<String, Set<String>> childToParent = new LinkedHashMap<>();
        LocalDate syncDate = expectedDate;

        for (ReportRow row : rows) {
            if (syncDate == null && row.date() != null) {
                syncDate = row.date();
            }
            if (!hasText(row.empCcgid()) || !hasText(row.empName()) || !hasText(row.empPositionId())) {
                continue;
            }
            people.putIfAbsent(
                    row.empCcgid(),
                    new PersonDraft(row.empCcgid(), row.empId(), row.empName(), row.center(), row.empPositionId()));
            personToPosition.computeIfAbsent(row.empCcgid(), ignored -> new LinkedHashSet<>()).add(row.empPositionId());
            positionToPerson.computeIfAbsent(row.empPositionId(), ignored -> new LinkedHashSet<>()).add(row.empCcgid());
            if (hasText(row.center())) {
                personToCenter.computeIfAbsent(row.empCcgid(), ignored -> new LinkedHashSet<>()).add(row.center());
            }
            if (!TimesheetRowValidator.isProductionLine(row)) {
                continue;
            }
            addPosition(positions, childToParent, row.empPositionId(), "PRODUCTION", row.supervisorPositionId());
            addPosition(
                    positions, childToParent, row.supervisorPositionId(), "SUPERVISOR", row.srManagerPositionId());
            addPosition(
                    positions,
                    childToParent,
                    row.srManagerPositionId(),
                    "SR_MANAGER",
                    row.domainHeadPositionId());
            addPosition(positions, childToParent, row.domainHeadPositionId(), "DOMAIN_HEAD", null);
        }

        addConflicts(issues, runId, now, "emp_ccgid", "emp_position_id", personToPosition, true);
        addConflicts(issues, runId, now, "emp_position_id", "emp_ccgid", positionToPerson, false);
        addConflicts(issues, runId, now, "emp_ccgid", "center", personToCenter, true);
        addConflicts(issues, runId, now, "position_id", "parent_position_id", childToParent, false);

        if (syncDate == null) {
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    TimesheetSyncErrorCode.INVALID_DATE,
                    "Daily file has no valid date.",
                    null,
                    null,
                    null,
                    null,
                    null,
                    now));
        }
        if (people.isEmpty() && positions.isEmpty() && issues.isEmpty()) {
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    TimesheetSyncErrorCode.EMPTY_FILE,
                    "Daily file produced no person or position rows.",
                    null,
                    null,
                    null,
                    null,
                    null,
                    now));
        }
        return new Result(
                syncDate,
                people.values().stream()
                        .map(draft -> TimesheetPerson.create(
                                runId, draft.ccgid, draft.empId, draft.center, draft.name, draft.positionId))
                        .toList(),
                positions.values().stream()
                        .map(draft -> TimesheetPosition.create(
                                runId, draft.positionId, draft.roleType, draft.parentPositionId))
                        .toList(),
                issues);
    }

    private static void addPosition(
            Map<String, PositionDraft> positions,
            Map<String, Set<String>> childToParent,
            String positionId,
            String roleType,
            String parent) {
        if (!hasText(positionId)) {
            return;
        }
        positions.putIfAbsent(positionId, new PositionDraft(positionId, roleType, parent));
        if (hasText(parent)) {
            childToParent.computeIfAbsent(positionId, ignored -> new LinkedHashSet<>()).add(parent);
        }
    }

    private static void addConflicts(
            List<TimesheetSyncIssue> issues,
            UUID runId,
            Instant now,
            String childLabel,
            String parentLabel,
            Map<String, Set<String>> edges,
            boolean childIsCcgid) {
        for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            TimesheetSyncErrorCode code = "emp_ccgid".equals(childLabel) && "emp_position_id".equals(parentLabel)
                    ? TimesheetSyncErrorCode.PERSON_POSITION_CONFLICT
                    : "emp_position_id".equals(childLabel)
                            ? TimesheetSyncErrorCode.OCCUPANCY_CONFLICT
                            : TimesheetSyncErrorCode.HIERARCHY_CONFLICT;
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    code,
                    childLabel + " maps to multiple " + parentLabel + ": "
                            + String.join(", ", entry.getValue()),
                    null,
                    childIsCcgid ? entry.getKey() : null,
                    childIsCcgid ? null : entry.getKey(),
                    null,
                    null,
                    now));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record PersonDraft(
            String ccgid, String empId, String name, String center, String positionId) {
    }

    private record PositionDraft(String positionId, String roleType, String parentPositionId) {
    }
}
