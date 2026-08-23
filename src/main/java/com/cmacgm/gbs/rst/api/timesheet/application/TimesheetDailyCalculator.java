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
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetAssignment;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetOccupancy;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPosition;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetScope;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;

/**
 * Builds Daily computed tables and hierarchy ERROR issues from one file scan.
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
            List<TimesheetOccupancy> occupancies,
            List<TimesheetScope> scopes,
            List<TimesheetAssignment> assignments,
            List<TimesheetSyncIssue> issues) {
    }

    /**
     * Computes Daily tables for a run.
     *
     * @param runId Daily run
     * @param rows parsed rows
     * @param now issue timestamp
     * @return result
     */
    public Result compute(UUID runId, List<ReportRow> rows, Instant now) {
        Map<String, PersonDraft> people = new LinkedHashMap<>();
        Map<String, PositionDraft> positions = new LinkedHashMap<>();
        Map<String, OccupancyDraft> occupancies = new LinkedHashMap<>();
        Map<String, ScopeDraft> scopes = new LinkedHashMap<>();
        Map<String, AssignmentDraft> assignments = new LinkedHashMap<>();
        Map<String, Set<String>> empToSupervisor = new LinkedHashMap<>();
        Map<String, Set<String>> personToCenter = new LinkedHashMap<>();
        Map<String, Set<String>> supervisorToManager = new LinkedHashMap<>();
        Map<String, Set<String>> managerToDomainHead = new LinkedHashMap<>();
        Map<String, Set<String>> occupancyByPosition = new LinkedHashMap<>();
        Map<String, Set<String>> assignmentSupervisors = new LinkedHashMap<>();
        List<TimesheetSyncIssue> issues = new ArrayList<>();

        LocalDate syncDate = null;
        for (ReportRow row : rows) {
            if (syncDate == null && row.date() != null) {
                syncDate = row.date();
            }
            addPerson(
                    people,
                    row.empCcgid(),
                    row.empId(),
                    row.empName(),
                    firstText(row.empPositionId(), row.empId()),
                    1,
                    row.center());
            addPerson(
                    people,
                    row.supervisorCcgid(),
                    row.supervisorId(),
                    row.supervisorName(),
                    row.supervisorPositionId(),
                    2,
                    row.center());
            addPerson(
                    people,
                    row.srManagerCcgid(),
                    row.srManagerId(),
                    row.srManagerName(),
                    row.srManagerPositionId(),
                    3,
                    row.center());
            addPerson(
                    people,
                    row.domainHeadCcgid(),
                    row.domainHeadId(),
                    row.domainHeadName(),
                    row.domainHeadPositionId(),
                    4,
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

            addOccupancy(
                    occupancies,
                    occupancyByPosition,
                    row.supervisorPositionId(),
                    row.supervisorCcgid(),
                    row.supervisorId());
            addOccupancy(
                    occupancies,
                    occupancyByPosition,
                    row.srManagerPositionId(),
                    row.srManagerCcgid(),
                    row.srManagerId());
            addOccupancy(
                    occupancies,
                    occupancyByPosition,
                    row.domainHeadPositionId(),
                    row.domainHeadCcgid(),
                    row.domainHeadId());

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

            putEdge(empToSupervisor, row.empCcgid(), row.supervisorPositionId());
            putEdge(supervisorToManager, row.supervisorPositionId(), row.srManagerPositionId());
            putEdge(managerToDomainHead, row.srManagerPositionId(), row.domainHeadPositionId());
        }

        addConflicts(issues, runId, now, "emp_ccgid", "supervisor_position_id", empToSupervisor, true);
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
        for (Map.Entry<String, Set<String>> entry : occupancyByPosition.entrySet()) {
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
        for (Map.Entry<String, Set<String>> entry : assignmentSupervisors.entrySet()) {
            if (entry.getValue().size() > 1) {
                issues.add(TimesheetSyncIssue.error(
                        runId,
                        "ASSIGNMENT_CONFLICT",
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
                    runId, "INVALID_DATE", "Daily file has no valid date.", null, null, null, null, null, now));
        }

        return new Result(
                syncDate,
                people.values().stream()
                        .map(draft -> TimesheetPerson.create(
                                runId,
                                draft.ccgid,
                                draft.empId,
                                draft.empPositionId,
                                draft.center,
                                draft.name))
                        .toList(),
                positions.values().stream()
                        .map(draft -> TimesheetPosition.create(
                                runId, draft.positionId, draft.roleType, draft.parentPositionId))
                        .toList(),
                occupancies.values().stream()
                        .map(draft -> TimesheetOccupancy.create(
                                runId, draft.positionId, draft.empCcgid, draft.empId))
                        .toList(),
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
                issues);
    }

    private static void addPerson(
            Map<String, PersonDraft> people,
            String ccgid,
            String empId,
            String name,
            String empPositionId,
            int positionRank,
            String center) {
        if (!hasText(ccgid) || !hasText(name)) {
            return;
        }
        PersonDraft existing = people.get(ccgid);
        if (existing == null) {
            people.put(ccgid, new PersonDraft(ccgid, empId, name, empPositionId, positionRank, center));
            return;
        }
        if (!hasText(existing.empId) && hasText(empId)) {
            existing.empId = empId;
        }
        if (hasText(empPositionId) && positionRank >= existing.positionRank) {
            existing.empPositionId = empPositionId;
            existing.positionRank = positionRank;
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

    private static void addOccupancy(
            Map<String, OccupancyDraft> occupancies,
            Map<String, Set<String>> occupancyByPosition,
            String positionId,
            String ccgid,
            String empId) {
        if (!hasText(positionId) || !hasText(ccgid)) {
            return;
        }
        occupancies.putIfAbsent(positionId, new OccupancyDraft(positionId, ccgid, empId));
        occupancyByPosition.computeIfAbsent(positionId, ignored -> new LinkedHashSet<>()).add(ccgid);
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

    private static String key(String... parts) {
        return String.join("|", parts);
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
        private String empPositionId;
        private int positionRank;
        private String center;

        private PersonDraft(
                String ccgid,
                String empId,
                String name,
                String empPositionId,
                int positionRank,
                String center) {
            this.ccgid = ccgid;
            this.empId = empId;
            this.name = name;
            this.empPositionId = empPositionId;
            this.positionRank = hasText(empPositionId) ? positionRank : 0;
            this.center = center;
        }
    }

    private record PositionDraft(String positionId, String roleType, String parentPositionId) {
    }

    private record OccupancyDraft(String positionId, String empCcgid, String empId) {
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
}
