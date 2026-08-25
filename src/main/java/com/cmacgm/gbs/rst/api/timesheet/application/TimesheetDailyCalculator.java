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
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;

/**
 * Builds Daily person and position tables from one file scan.
 * One person occupies exactly one bindable position.
 */
@Component
public class TimesheetDailyCalculator {

    private static final int RANK_EMPLOYEE = 0;
    private static final int RANK_SUPERVISOR = 1;
    private static final int RANK_SR_MANAGER = 2;
    private static final int RANK_DOMAIN_HEAD = 3;

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
        Map<String, PersonDraft> people = new LinkedHashMap<>();
        Map<String, PositionDraft> positions = new LinkedHashMap<>();
        Map<String, Set<String>> personToCenter = new LinkedHashMap<>();
        Map<String, Set<String>> personToPositions = new LinkedHashMap<>();
        Map<String, Set<String>> positionToPeople = new LinkedHashMap<>();
        Map<String, Set<String>> supervisorToManager = new LinkedHashMap<>();
        Map<String, Set<String>> managerToDomainHead = new LinkedHashMap<>();
        List<TimesheetSyncIssue> issues = new ArrayList<>();

        LocalDate syncDate = null;
        for (ReportRow row : rows) {
            if (syncDate == null && row.date() != null) {
                syncDate = row.date();
            }
            addPerson(people, row.empCcgid(), row.empId(), row.empName(), row.center());
            addPerson(
                    people,
                    row.supervisorCcgid(),
                    row.supervisorId(),
                    row.supervisorName(),
                    row.center());
            addPerson(
                    people,
                    row.srManagerCcgid(),
                    row.srManagerId(),
                    row.srManagerName(),
                    row.center());
            addPerson(
                    people,
                    row.domainHeadCcgid(),
                    row.domainHeadId(),
                    row.domainHeadName(),
                    row.center());
            putEdge(personToCenter, row.empCcgid(), row.center());
            putEdge(personToCenter, row.supervisorCcgid(), row.center());
            putEdge(personToCenter, row.srManagerCcgid(), row.center());
            putEdge(personToCenter, row.domainHeadCcgid(), row.center());

            addPosition(
                    positions,
                    "SUPERVISOR",
                    row.supervisorPositionId(),
                    row.srManagerPositionId());
            addPosition(
                    positions,
                    "SR_MANAGER",
                    row.srManagerPositionId(),
                    row.domainHeadPositionId());
            addPosition(positions, "DOMAIN_HEAD", row.domainHeadPositionId(), null);

            bindSeat(
                    people,
                    personToPositions,
                    positionToPeople,
                    row.empCcgid(),
                    firstText(row.empPositionId(), row.empId()),
                    RANK_EMPLOYEE);
            bindSeat(
                    people,
                    personToPositions,
                    positionToPeople,
                    row.supervisorCcgid(),
                    row.supervisorPositionId(),
                    RANK_SUPERVISOR);
            bindSeat(
                    people,
                    personToPositions,
                    positionToPeople,
                    row.srManagerCcgid(),
                    row.srManagerPositionId(),
                    RANK_SR_MANAGER);
            bindSeat(
                    people,
                    personToPositions,
                    positionToPeople,
                    row.domainHeadCcgid(),
                    row.domainHeadPositionId(),
                    RANK_DOMAIN_HEAD);

            putEdge(supervisorToManager, row.supervisorPositionId(), row.srManagerPositionId());
            putEdge(managerToDomainHead, row.srManagerPositionId(), row.domainHeadPositionId());
        }

        addConflicts(issues, runId, now, "emp_ccgid", "center", personToCenter, true);
        addConflicts(
                issues, runId, now, "supervisor_position_id", "sr_manager_position_id", supervisorToManager, false);
        addConflicts(
                issues,
                runId,
                now,
                "sr_manager_position_id",
                "domain_head_position_id",
                managerToDomainHead,
                false);
        for (Map.Entry<String, Set<String>> entry : personToPositions.entrySet()) {
            if (entry.getValue().size() > 1) {
                issues.add(TimesheetSyncIssue.error(
                        runId,
                        "PERSON_POSITION_CONFLICT",
                        "emp_ccgid maps to multiple position_id: " + String.join(", ", entry.getValue()),
                        null,
                        entry.getKey(),
                        null,
                        null,
                        null,
                        now));
            }
        }
        for (Map.Entry<String, Set<String>> entry : positionToPeople.entrySet()) {
            if (entry.getValue().size() > 1) {
                issues.add(TimesheetSyncIssue.error(
                        runId,
                        "OCCUPANCY_CONFLICT",
                        "position_id maps to multiple emp_ccgid: " + String.join(", ", entry.getValue()),
                        null,
                        null,
                        entry.getKey(),
                        null,
                        null,
                        now));
            }
        }

        if (syncDate == null) {
            issues.add(TimesheetSyncIssue.error(
                    runId, "INVALID_DATE", "Daily file has no valid date.", null, null, null, null, null, now));
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

    private static void addPerson(
            Map<String, PersonDraft> people,
            String ccgid,
            String empId,
            String name,
            String center) {
        if (!hasText(ccgid) || !hasText(name)) {
            return;
        }
        PersonDraft existing = people.get(ccgid);
        if (existing == null) {
            people.put(ccgid, new PersonDraft(ccgid, empId, name, center));
            return;
        }
        if (!hasText(existing.empId) && hasText(empId)) {
            existing.empId = empId;
        }
        if (!hasText(existing.center) && hasText(center)) {
            existing.center = center;
        }
    }

    private static void addPosition(
            Map<String, PositionDraft> positions, String roleType, String positionId, String parent) {
        if (!hasText(positionId)) {
            return;
        }
        positions.putIfAbsent(positionId, new PositionDraft(positionId, roleType, parent));
    }

    private static void bindSeat(
            Map<String, PersonDraft> people,
            Map<String, Set<String>> personToPositions,
            Map<String, Set<String>> positionToPeople,
            String ccgid,
            String positionId,
            int rank) {
        if (!hasText(ccgid) || !hasText(positionId)) {
            return;
        }
        putEdge(personToPositions, ccgid, positionId);
        putEdge(positionToPeople, positionId, ccgid);
        PersonDraft draft = people.get(ccgid);
        if (draft == null) {
            return;
        }
        if (!hasText(draft.positionId) || rank >= draft.positionRank) {
            draft.positionId = positionId;
            draft.positionRank = rank;
        }
    }

    private static void putEdge(Map<String, Set<String>> edges, String child, String parent) {
        if (!hasText(child) || !hasText(parent)) {
            return;
        }
        edges.computeIfAbsent(child, ignored -> new LinkedHashSet<>()).add(parent);
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
            issues.add(TimesheetSyncIssue.error(
                    runId,
                    "HIERARCHY_CONFLICT",
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

    private static String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private static final class PersonDraft {
        private final String ccgid;
        private String empId;
        private final String name;
        private String center;
        private String positionId;
        private int positionRank = Integer.MIN_VALUE;

        private PersonDraft(String ccgid, String empId, String name, String center) {
            this.ccgid = ccgid;
            this.empId = empId;
            this.name = name;
            this.center = center;
        }
    }

    private record PositionDraft(String positionId, String roleType, String parentPositionId) {
    }
}
