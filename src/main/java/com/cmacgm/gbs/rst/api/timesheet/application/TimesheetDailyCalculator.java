package com.cmacgm.gbs.rst.api.timesheet.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReportParser.ReportRow;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPersonPositionRole;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPosition;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPositionParent;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncIssue;

/**
 * Daily people come from every complete identity on the file, including
 * Domain Head. Positions, parent edges and occupancies are built from
 * RST-applicable Production + Productive rows through Domain Head. A node
 * may have more than one parent; every distinct edge is kept. Date and
 * Center come from the file name. One person may hold more than one role
 * on the same position. {@code hc} is ignored.
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
            List<TimesheetPositionParent> parents,
            List<TimesheetPersonPositionRole> seats,
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
     * Validates then collects people, then builds positions.
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
        List<TimesheetSyncIssue> issues = new ArrayList<>(TimesheetRowValidator.validateDailyPeople(runId, rows, now));
        if (hasBlocking(issues)) {
            addFileDateIfMissing(issues, runId, now, syncDate);
            return new Result(syncDate, List.of(), List.of(), List.of(), List.of(), issues);
        }

        Map<String, PersonDraft> people = new LinkedHashMap<>();
        for (ReportRow row : rows) {
            rememberPeopleFromRow(people, row, snapshotCenter);
        }

        issues.addAll(TimesheetRowValidator.validateDailyPositions(runId, rows, now, processes));
        if (hasBlocking(issues)) {
            addEmptyFileIfNeeded(issues, runId, now, people, List.of());
            return toResult(runId, syncDate, people, List.of(), List.of(), List.of(), issues);
        }

        Map<String, PositionDraft> positions = new LinkedHashMap<>();
        Map<String, ParentEdge> parents = new LinkedHashMap<>();
        Map<String, SeatDraft> seats = new LinkedHashMap<>();
        for (ReportRow row : rows) {
            if (!processes.applies(row.pl3Code()) || !TimesheetRowValidator.isProductionLine(row)) {
                continue;
            }
            addPosition(positions, row.empPositionId(), "AGENT");
            addPosition(positions, row.supervisorPositionId(), "SUPERVISOR");
            addPosition(positions, row.srManagerPositionId(), "SR_MANAGER");
            addPosition(positions, row.domainHeadPositionId(), "DOMAIN_HEAD");
            addEdge(parents, row.empPositionId(), "AGENT", row.supervisorPositionId(), "SUPERVISOR");
            addEdge(parents, row.supervisorPositionId(), "SUPERVISOR", row.srManagerPositionId(), "SR_MANAGER");
            addEdge(parents, row.srManagerPositionId(), "SR_MANAGER", row.domainHeadPositionId(), "DOMAIN_HEAD");
            addSeat(seats, row.empCcgid(), row.empName(), row.empPositionId(), "AGENT");
            addSeat(seats, row.supervisorCcgid(), row.supervisorName(), row.supervisorPositionId(), "SUPERVISOR");
            addSeat(seats, row.srManagerCcgid(), row.srManagerName(), row.srManagerPositionId(), "SR_MANAGER");
            addSeat(seats, row.domainHeadCcgid(), row.domainHeadName(), row.domainHeadPositionId(), "DOMAIN_HEAD");
        }

        addFileDateIfMissing(issues, runId, now, syncDate);
        addEmptyFileIfNeeded(issues, runId, now, people, positions.values());
        return toResult(runId, syncDate, people, positions.values(), parents.values(), seats.values(), issues);
    }

    private static Result toResult(
            UUID runId,
            LocalDate syncDate,
            Map<String, PersonDraft> people,
            java.util.Collection<PositionDraft> positions,
            java.util.Collection<ParentEdge> parents,
            java.util.Collection<SeatDraft> seats,
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
                                draft.jobRole))
                        .toList(),
                positions.stream()
                        .map(draft -> TimesheetPosition.create(runId, draft.positionId, draft.roleType))
                        .toList(),
                parents.stream()
                        .filter(edge -> hasNode(positions, edge.positionId, edge.roleType))
                        .filter(edge -> hasNode(positions, edge.parentPositionId, edge.parentRoleType))
                        .map(edge -> TimesheetPositionParent.create(
                                runId,
                                edge.positionId,
                                edge.roleType,
                                edge.parentPositionId,
                                edge.parentRoleType))
                        .toList(),
                seats.stream()
                        .filter(seat -> people.containsKey(seat.ccgid))
                        .filter(seat -> hasNode(positions, seat.positionId, seat.roleType))
                        .map(seat -> TimesheetPersonPositionRole.create(
                                runId, seat.ccgid, seat.positionId, seat.roleType))
                        .toList(),
                issues);
    }

    private static boolean hasNode(
            java.util.Collection<PositionDraft> positions, String positionId, String roleType) {
        return positions.stream().anyMatch(position ->
                position.positionId.equals(positionId) && position.roleType.equals(roleType));
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

    private static void rememberPeopleFromRow(
            Map<String, PersonDraft> people, ReportRow row, String snapshotCenter) {
        rememberPerson(
                people,
                row.empCcgid(),
                row.empId(),
                row.empName(),
                row.empEmail(),
                firstText(snapshotCenter, row.center()),
                row.empJobRole(),
                row.empPositionId());
        rememberPerson(
                people,
                row.supervisorCcgid(),
                row.supervisorId(),
                row.supervisorName(),
                null,
                null,
                null,
                row.supervisorPositionId());
        rememberPerson(
                people,
                row.srManagerCcgid(),
                row.srManagerId(),
                row.srManagerName(),
                null,
                null,
                null,
                row.srManagerPositionId());
        rememberPerson(
                people,
                row.domainHeadCcgid(),
                row.domainHeadId(),
                row.domainHeadName(),
                null,
                null,
                null,
                row.domainHeadPositionId());
    }

    private static void rememberPerson(
            Map<String, PersonDraft> people,
            String ccgid,
            String empId,
            String name,
            String email,
            String center,
            String jobRole,
            String positionId) {
        if (!hasText(ccgid) || !hasText(name) || !hasText(positionId)) {
            return;
        }
        String key = ccgid.trim().toUpperCase();
        PersonDraft incoming = new PersonDraft(key, empId, name, email, center, hasText(jobRole) ? jobRole : null);
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
                firstText(existing.jobRole, incoming.jobRole));
    }

    private static void addSeat(
            Map<String, SeatDraft> seats, String ccgid, String name, String positionId, String roleType) {
        if (!hasText(ccgid) || !hasText(name) || !hasText(positionId)) {
            return;
        }
        String key = ccgid.trim().toUpperCase() + "|" + positionId + "|" + roleType;
        seats.putIfAbsent(key, new SeatDraft(ccgid.trim().toUpperCase(), positionId, roleType));
    }

    private static void addPosition(Map<String, PositionDraft> positions, String positionId, String roleType) {
        if (!hasText(positionId)) {
            return;
        }
        positions.putIfAbsent(nodeKey(positionId, roleType), new PositionDraft(positionId, roleType));
    }

    private static void addEdge(
            Map<String, ParentEdge> parents,
            String positionId,
            String roleType,
            String parentPositionId,
            String parentRoleType) {
        if (!hasText(positionId) || !hasText(parentPositionId)) {
            return;
        }
        String key = nodeKey(positionId, roleType) + "|" + nodeKey(parentPositionId, parentRoleType);
        parents.putIfAbsent(key, new ParentEdge(positionId, roleType, parentPositionId, parentRoleType));
    }

    private static String nodeKey(String positionId, String roleType) {
        return positionId + "|" + roleType;
    }

    private static String firstText(String left, String right) {
        return hasText(left) ? left : right;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record PersonDraft(
            String ccgid, String empId, String name, String email, String center, String jobRole) {
    }

    private record PositionDraft(String positionId, String roleType) {
    }

    private record ParentEdge(
            String positionId, String roleType, String parentPositionId, String parentRoleType) {
    }

    private record SeatDraft(String ccgid, String positionId, String roleType) {
    }
}
