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
 * Daily People come from every complete identity on the file. The position
 * tree is built from RST-applicable Production + Productive rows. One person
 * on two seats is a blocking conflict; two people on one seat is allowed.
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
        return compute(runId, rows, now, null, GbsProcessCatalog.allowing());
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
        return compute(runId, rows, now, expectedDate, GbsProcessCatalog.allowing());
    }

    /**
     * Computes Daily org tables, checks the file-name date, and filters
     * positions by RST-applicable Production + Productive rows.
     *
     * @param runId Daily run
     * @param rows parsed rows
     * @param now issue timestamp
     * @param expectedDate date from the file name
     * @param catalog RST-applicable PL3 codes
     * @return result
     */
    public Result compute(
            UUID runId, List<ReportRow> rows, Instant now, LocalDate expectedDate, GbsProcessCatalog catalog) {
        GbsProcessCatalog processes = catalog == null ? GbsProcessCatalog.allowing() : catalog;
        List<TimesheetSyncIssue> issues = new ArrayList<>(
                TimesheetRowValidator.validate(runId, "DAILY", expectedDate, rows, now, processes));
        Map<String, PersonDraft> people = new LinkedHashMap<>();
        Map<String, Set<String>> personToPosition = new LinkedHashMap<>();
        Map<String, PositionDraft> positions = new LinkedHashMap<>();
        Map<String, Set<String>> childToParent = new LinkedHashMap<>();
        LocalDate syncDate = expectedDate;

        for (ReportRow row : rows) {
            if (syncDate == null && row.date() != null) {
                syncDate = row.date();
            }
            rememberPerson(
                    people,
                    personToPosition,
                    row.empCcgid(),
                    row.empId(),
                    row.empName(),
                    row.empEmail(),
                    row.center(),
                    row.empPositionId());
            if (!processes.applies(row.pl3Code()) || !TimesheetRowValidator.isProductionLine(row)) {
                continue;
            }
            rememberPerson(
                    people,
                    personToPosition,
                    row.supervisorCcgid(),
                    row.supervisorId(),
                    row.supervisorName(),
                    null,
                    null,
                    row.supervisorPositionId());
            rememberPerson(
                    people,
                    personToPosition,
                    row.srManagerCcgid(),
                    row.srManagerId(),
                    row.srManagerName(),
                    null,
                    null,
                    row.srManagerPositionId());
            rememberPerson(
                    people,
                    personToPosition,
                    row.domainHeadCcgid(),
                    row.domainHeadId(),
                    row.domainHeadName(),
                    null,
                    null,
                    row.domainHeadPositionId());
            addPosition(
                    positions,
                    childToParent,
                    row.empPositionId(),
                    "AGENT",
                    row.supervisorPositionId(),
                    row.center());
            addPosition(
                    positions,
                    childToParent,
                    row.supervisorPositionId(),
                    "SUPERVISOR",
                    row.srManagerPositionId(),
                    row.center());
            addPosition(
                    positions,
                    childToParent,
                    row.srManagerPositionId(),
                    "SR_MANAGER",
                    row.domainHeadPositionId(),
                    row.center());
            addPosition(
                    positions, childToParent, row.domainHeadPositionId(), "DOMAIN_HEAD", null, row.center());
        }

        dropPeopleOnMultipleSeats(issues, runId, now, people, personToPosition);
        addHierarchyConflicts(issues, runId, now, childToParent);

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
                                runId,
                                draft.ccgid,
                                draft.empId,
                                draft.center,
                                draft.name,
                                draft.email,
                                draft.positionId))
                        .toList(),
                positions.values().stream()
                        .map(draft -> TimesheetPosition.create(
                                runId, draft.positionId, draft.roleType, draft.parentPositionId, draft.center))
                        .toList(),
                issues);
    }

    private static void rememberPerson(
            Map<String, PersonDraft> people,
            Map<String, Set<String>> personToPosition,
            String ccgid,
            String empId,
            String name,
            String email,
            String center,
            String positionId) {
        if (!hasText(ccgid) || !hasText(name) || !hasText(positionId)) {
            return;
        }
        String key = ccgid.trim().toUpperCase();
        personToPosition.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(positionId);
        PersonDraft incoming = new PersonDraft(key, empId, name, email, center, positionId);
        PersonDraft existing = people.get(key);
        if (existing == null) {
            people.put(key, incoming);
            return;
        }
        people.put(key, merge(existing, incoming));
    }

    private static PersonDraft merge(PersonDraft existing, PersonDraft incoming) {
        return new PersonDraft(
                existing.ccgid,
                firstText(existing.empId, incoming.empId),
                firstText(existing.name, incoming.name),
                firstText(existing.email, incoming.email),
                firstText(existing.center, incoming.center),
                existing.positionId);
    }

    private static void dropPeopleOnMultipleSeats(
            List<TimesheetSyncIssue> issues,
            UUID runId,
            Instant now,
            Map<String, PersonDraft> people,
            Map<String, Set<String>> personToPosition) {
        for (Map.Entry<String, Set<String>> entry : personToPosition.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    TimesheetSyncErrorCode.PERSON_POSITION_CONFLICT,
                    "emp_ccgid " + entry.getKey() + " maps to multiple emp_position_id: "
                            + String.join(", ", entry.getValue()),
                    null,
                    entry.getKey(),
                    null,
                    null,
                    null,
                    now));
            people.remove(entry.getKey());
        }
    }

    private static void addHierarchyConflicts(
            List<TimesheetSyncIssue> issues, UUID runId, Instant now, Map<String, Set<String>> childToParent) {
        for (Map.Entry<String, Set<String>> entry : childToParent.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    TimesheetSyncErrorCode.HIERARCHY_CONFLICT,
                    "position_id " + entry.getKey() + " maps to multiple parent_position_id: "
                            + String.join(", ", entry.getValue()),
                    null,
                    null,
                    entry.getKey(),
                    null,
                    null,
                    now));
        }
    }

    private static void addPosition(
            Map<String, PositionDraft> positions,
            Map<String, Set<String>> childToParent,
            String positionId,
            String roleType,
            String parent,
            String center) {
        if (!hasText(positionId)) {
            return;
        }
        positions.putIfAbsent(
                positionId, new PositionDraft(positionId, roleType, parent, hasText(center) ? center : ""));
        if (hasText(parent)) {
            childToParent.computeIfAbsent(positionId, ignored -> new LinkedHashSet<>()).add(parent);
        }
    }

    private static String firstText(String left, String right) {
        return hasText(left) ? left : right;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record PersonDraft(
            String ccgid, String empId, String name, String email, String center, String positionId) {
    }

    private record PositionDraft(String positionId, String roleType, String parentPositionId, String center) {
    }
}
