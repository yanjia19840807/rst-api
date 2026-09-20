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
 * Daily People come from every complete identity on the file, including
 * supervisor / Sr Manager / Domain Head columns on non-RST or non-Production
 * rows. Person required fields are checked first; a failure records issues
 * and skips both people and positions. Position required fields, unique
 * parent chain and one-person-one-seat are checked before the tree is
 * built from RST-applicable Production + Productive rows. Date and
 * Center come from the file name. Two people on one seat is allowed.
 * {@code hc} is ignored.
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
     * Validates then collects people from every row. Validates then builds
     * the position tree from RST-applicable Production + Productive rows.
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
        return compute(runId, rows, now, expectedDate, catalog, null);
    }

    /**
     * Validates then collects people, then builds positions. Date and
     * Center come from the file name when present.
     *
     * @param runId Daily run
     * @param rows parsed rows
     * @param now issue timestamp
     * @param expectedDate date from the file name
     * @param catalog RST-applicable PL3 codes
     * @param center Center from the file name
     * @return result
     */
    public Result compute(
            UUID runId,
            List<ReportRow> rows,
            Instant now,
            LocalDate expectedDate,
            GbsProcessCatalog catalog,
            String center) {
        GbsProcessCatalog processes = catalog == null ? GbsProcessCatalog.allowing() : catalog;
        LocalDate syncDate = expectedDate != null ? expectedDate : firstRowDate(rows);
        String snapshotCenter = hasText(center) ? center.trim() : null;
        List<TimesheetSyncIssue> issues = new ArrayList<>(
                TimesheetRowValidator.validateDailyPeople(runId, rows, now));
        if (hasBlocking(issues)) {
            addFileDateIfMissing(issues, runId, now, syncDate);
            return new Result(syncDate, List.of(), List.of(), issues);
        }

        Map<String, PersonDraft> people = new LinkedHashMap<>();
        for (ReportRow row : rows) {
            rememberPeopleFromRow(people, null, row, snapshotCenter);
        }

        issues.addAll(TimesheetRowValidator.validateDailyPositions(runId, rows, now, processes));
        Map<String, Set<String>> personToPosition = new LinkedHashMap<>();
        Map<String, Set<String>> childToParent = new LinkedHashMap<>();
        collectPositionGraph(rows, processes, personToPosition, childToParent);
        addSeatConflicts(issues, runId, now, personToPosition);
        addHierarchyConflicts(issues, runId, now, childToParent);
        if (hasBlocking(issues)) {
            addEmptyFileIfNeeded(issues, runId, now, people, List.of());
            return toResult(runId, syncDate, people, List.of(), issues);
        }

        Map<String, PositionDraft> positions = new LinkedHashMap<>();
        for (ReportRow row : rows) {
            if (!processes.applies(row.pl3Code()) || !TimesheetRowValidator.isProductionLine(row)) {
                continue;
            }
            String positionCenter = firstText(snapshotCenter, row.center());
            addPosition(positions, row.empPositionId(), "AGENT", row.supervisorPositionId(), positionCenter);
            addPosition(positions, row.supervisorPositionId(), "SUPERVISOR", row.srManagerPositionId(), positionCenter);
            addPosition(positions, row.srManagerPositionId(), "SR_MANAGER", row.domainHeadPositionId(), positionCenter);
            addPosition(positions, row.domainHeadPositionId(), "DOMAIN_HEAD", null, positionCenter);
        }

        addFileDateIfMissing(issues, runId, now, syncDate);
        addEmptyFileIfNeeded(issues, runId, now, people, positions.values());
        return toResult(runId, syncDate, people, positions.values(), issues);
    }

    private static Result toResult(
            UUID runId,
            LocalDate syncDate,
            Map<String, PersonDraft> people,
            java.util.Collection<PositionDraft> positions,
            List<TimesheetSyncIssue> issues) {
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
                                draft.positionId,
                                draft.jobRole))
                        .toList(),
                positions.stream()
                        .map(draft -> TimesheetPosition.create(
                                runId, draft.positionId, draft.roleType, draft.parentPositionId, draft.center))
                        .toList(),
                issues);
    }

    private static LocalDate firstRowDate(List<ReportRow> rows) {
        return rows.stream().map(ReportRow::date).filter(date -> date != null).findFirst().orElse(null);
    }

    private static boolean hasBlocking(List<TimesheetSyncIssue> issues) {
        return issues.stream().anyMatch(issue -> !TimesheetRowValidator.isAdvisory(issue, "DAILY"));
    }

    private static void addFileDateIfMissing(
            List<TimesheetSyncIssue> issues, UUID runId, Instant now, LocalDate syncDate) {
        if (syncDate != null) {
            return;
        }
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

    private static void addEmptyFileIfNeeded(
            List<TimesheetSyncIssue> issues,
            UUID runId,
            Instant now,
            Map<String, PersonDraft> people,
            java.util.Collection<?> positions) {
        if (!people.isEmpty() || !positions.isEmpty() || !issues.isEmpty()) {
            return;
        }
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

    private static void collectPositionGraph(
            List<ReportRow> rows,
            GbsProcessCatalog processes,
            Map<String, Set<String>> personToPosition,
            Map<String, Set<String>> childToParent) {
        for (ReportRow row : rows) {
            if (!processes.applies(row.pl3Code()) || !TimesheetRowValidator.isProductionLine(row)) {
                continue;
            }
            trackSeat(personToPosition, row.empCcgid(), row.empPositionId());
            trackSeat(personToPosition, row.supervisorCcgid(), row.supervisorPositionId());
            trackSeat(personToPosition, row.srManagerCcgid(), row.srManagerPositionId());
            trackSeat(personToPosition, row.domainHeadCcgid(), row.domainHeadPositionId());
            trackParent(childToParent, row.empPositionId(), row.supervisorPositionId());
            trackParent(childToParent, row.supervisorPositionId(), row.srManagerPositionId());
            trackParent(childToParent, row.srManagerPositionId(), row.domainHeadPositionId());
        }
    }

    private static void trackSeat(Map<String, Set<String>> personToPosition, String ccgid, String positionId) {
        if (!hasText(ccgid) || !hasText(positionId)) {
            return;
        }
        personToPosition.computeIfAbsent(ccgid.trim().toUpperCase(), ignored -> new LinkedHashSet<>()).add(positionId);
    }

    private static void trackParent(Map<String, Set<String>> childToParent, String positionId, String parent) {
        if (!hasText(positionId) || !hasText(parent)) {
            return;
        }
        childToParent.computeIfAbsent(positionId, ignored -> new LinkedHashSet<>()).add(parent);
    }

    private static void rememberPeopleFromRow(
            Map<String, PersonDraft> people,
            Map<String, Set<String>> personToPosition,
            ReportRow row,
            String snapshotCenter) {
        rememberPerson(
                people,
                personToPosition,
                row.empCcgid(),
                row.empId(),
                row.empName(),
                row.empEmail(),
                firstText(snapshotCenter, row.center()),
                row.empPositionId(),
                row.empJobRole());
        rememberPerson(
                people,
                personToPosition,
                row.supervisorCcgid(),
                row.supervisorId(),
                row.supervisorName(),
                null,
                null,
                row.supervisorPositionId(),
                null);
        rememberPerson(
                people,
                personToPosition,
                row.srManagerCcgid(),
                row.srManagerId(),
                row.srManagerName(),
                null,
                null,
                row.srManagerPositionId(),
                null);
        rememberPerson(
                people,
                personToPosition,
                row.domainHeadCcgid(),
                row.domainHeadId(),
                row.domainHeadName(),
                null,
                null,
                row.domainHeadPositionId(),
                null);
    }

    private static void rememberPerson(
            Map<String, PersonDraft> people,
            Map<String, Set<String>> personToPosition,
            String ccgid,
            String empId,
            String name,
            String email,
            String center,
            String positionId,
            String jobRole) {
        if (!hasText(ccgid) || !hasText(name) || !hasText(positionId)) {
            return;
        }
        String key = ccgid.trim().toUpperCase();
        if (personToPosition != null) {
            personToPosition.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(positionId);
        }
        PersonDraft incoming = new PersonDraft(key, empId, name, email, center, positionId, jobRole);
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
                existing.positionId,
                firstText(existing.jobRole, incoming.jobRole));
    }

    private static void addSeatConflicts(
            List<TimesheetSyncIssue> issues,
            UUID runId,
            Instant now,
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
            String positionId,
            String roleType,
            String parent,
            String center) {
        if (!hasText(positionId)) {
            return;
        }
        positions.putIfAbsent(
                positionId,
                new PositionDraft(
                        positionId, roleType, hasText(parent) ? parent : null, hasText(center) ? center : ""));
    }

    private static String firstText(String left, String right) {
        return hasText(left) ? left : right;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record PersonDraft(
            String ccgid,
            String empId,
            String name,
            String email,
            String center,
            String positionId,
            String jobRole) {
    }

    private record PositionDraft(String positionId, String roleType, String parentPositionId, String center) {
    }
}
