package com.cmacgm.gbs.rst.api.sourcedata.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.security.RstCenters;
import com.cmacgm.gbs.rst.api.sourcedata.application.MyHrWorkbookParser.PersonRow;
import com.cmacgm.gbs.rst.api.sourcedata.application.MyHrWorkbookParser.PositionRow;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPosition;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPositionParent;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgSeat;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceIssue;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * Keeps qualified people for MyHR + SKPI centers. Roles and parent edges are
 * written when the file can support them. A missing parent does not reject the file.
 * An active parent with no occupant is still stored, with its chain role and
 * parent edge, so a Domain Head or Local Transformation Head can assign a delegate.
 * The missing-occupant issue stays. A parent occupied only outside a MyHR center,
 * an inactive parent, or a parent with more than one occupant does not get that edge.
 */
@Component
public class MyHrOrgCalculator {

    private static final Map<String, String> COUNTRY_CENTER = Map.of(
            "IND", RstCenters.GBS_INDIA,
            "CHN", RstCenters.GBS_CHINA,
            "CRI", RstCenters.GBS_COSTA_RICA,
            "LBN", RstCenters.GBS_LEBANON,
            "EST", RstCenters.GBS_ESTONIA,
            "PHL", RstCenters.GBS_PHILIPPINES,
            "PRT", RstCenters.GBS_PORTUGAL);

    /**
     * @param people kept people
     * @param positions position nodes
     * @param parents parent edges
     * @param seats seats
     * @param issues row issues
     * @param activate false when nothing remains
     */
    public record Result(
            List<OrgPerson> people,
            List<OrgPosition> positions,
            List<OrgPositionParent> parents,
            List<OrgSeat> seats,
            List<SourceIssue> issues,
            boolean activate) {
    }

    /**
     * Latest event date among parsed people, or {@code fallback}.
     *
     * @param rows parsed people
     * @param fallback when no date is present
     * @return sync date
     */
    public static LocalDate syncDate(List<PersonRow> rows, LocalDate fallback) {
        return rows.stream()
                .map(PersonRow::eventDate)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(fallback);
    }

    /**
     * @param runId snapshot
     * @param people parsed people
     * @param positions parsed positions
     * @param myhrCenters centers configured as MyHR + SKPI
     * @param now issue time
     * @return org rows
     */
    public Result compute(
            UUID runId,
            List<PersonRow> people,
            List<PositionRow> positions,
            Set<String> myhrCenters,
            Instant now) {
        List<SourceIssue> issues = new ArrayList<>();
        Map<String, PersonRow> latest = latestByPerson(people, runId, issues, now);
        List<Kept> qualified = qualify(latest.values(), myhrCenters);
        Map<String, PositionRow> activePositions = activePositions(positions, runId, issues, now);
        Set<String> occupiedOutside = occupiedOutside(latest.values(), myhrCenters, activePositions);
        List<Kept> seated = new ArrayList<>();
        Map<String, List<Kept>> byPosition = new LinkedHashMap<>();
        for (Kept person : qualified) {
            if (person.positionCode == null) {
                issues.add(issue(
                        runId, now, TimesheetSyncErrorCode.MISSING_FIELD, "Position code is empty.", person));
                continue;
            }
            PositionRow position = activePositions.get(person.positionCode);
            if (position == null || !isActive(position.status())) {
                issues.add(issue(
                        runId,
                        now,
                        TimesheetSyncErrorCode.INACTIVE_POSITION,
                        "Position " + person.positionCode + " is not active.",
                        person));
                continue;
            }
            seated.add(person);
            byPosition.computeIfAbsent(person.positionCode, key -> new ArrayList<>()).add(person);
        }
        Set<String> supervisors = new LinkedHashSet<>();
        Set<String> managers = new LinkedHashSet<>();
        for (Kept person : seated) {
            if (!isZero(person.row.directReports())) {
                continue;
            }
            String parent = parentCode(activePositions, person.positionCode);
            if (parent != null && chainPosition(parent, byPosition, occupiedOutside, activePositions)) {
                supervisors.add(parent);
            }
        }
        for (String supervisor : supervisors) {
            String grandparent = parentCode(activePositions, supervisor);
            if (grandparent != null && chainPosition(grandparent, byPosition, occupiedOutside, activePositions)) {
                managers.add(grandparent);
            }
        }
        Map<String, SeatDraft> seats = new LinkedHashMap<>();
        for (Kept person : seated) {
            addRoles(runId, now, issues, seats, person, supervisors, managers);
        }
        List<OrgPositionParent> edges = edges(
                runId, now, issues, seats, byPosition, activePositions, supervisors, managers);
        List<OrgPerson> storedPeople = peopleOf(runId, qualified);
        List<OrgPosition> storedPositions = positionsOf(runId, seats.values(), supervisors, managers, byPosition);
        List<OrgSeat> storedSeats = seats.values().stream()
                .map(seat -> OrgSeat.create(runId, seat.ccgid, seat.positionId, seat.roleType))
                .toList();
        if (storedPeople.isEmpty() && issues.isEmpty()) {
            issues.add(SourceIssue.error(
                    runId,
                    TimesheetSyncErrorCode.EMPTY_FILE,
                    "No rows remained after filtering.",
                    null,
                    null,
                    null,
                    null,
                    null,
                    now));
        }
        return new Result(
                storedPeople,
                storedPositions,
                edges,
                storedSeats,
                issues,
                !storedPeople.isEmpty());
    }

    private static Map<String, PersonRow> latestByPerson(
            List<PersonRow> people, UUID runId, List<SourceIssue> issues, Instant now) {
        Map<String, PersonRow> latest = new LinkedHashMap<>();
        for (PersonRow row : people) {
            String ccgid = SourceValues.fit(row.username(), 32);
            if (ccgid == null) {
                issues.add(SourceIssue.error(
                        runId,
                        TimesheetSyncErrorCode.MISSING_FIELD,
                        "Person has no CCGID.",
                        SourceValues.fit(row.personId(), 80),
                        null,
                        SourceValues.fit(row.positionCode(), 80),
                        null,
                        row.sourceRow(),
                        now));
                continue;
            }
            PersonRow current = latest.get(ccgid);
            if (current == null || newer(row, current)) {
                latest.put(ccgid, row);
            }
        }
        return latest;
    }

    private static boolean newer(PersonRow candidate, PersonRow current) {
        if (candidate.eventDate() == null && current.eventDate() == null) {
            return candidate.sourceRow() >= current.sourceRow();
        }
        if (candidate.eventDate() == null) {
            return false;
        }
        if (current.eventDate() == null) {
            return true;
        }
        int compared = candidate.eventDate().compareTo(current.eventDate());
        if (compared != 0) {
            return compared > 0;
        }
        return candidate.sourceRow() >= current.sourceRow();
    }

    private static List<Kept> qualify(Iterable<PersonRow> rows, Set<String> myhrCenters) {
        List<Kept> kept = new ArrayList<>();
        Set<String> centers = myhrCenters == null ? Set.of() : myhrCenters;
        for (PersonRow row : rows) {
            if (!includedStatus(row.status())) {
                continue;
            }
            String center = centerOf(row.companyCountry());
            if (center == null || !centers.contains(center)) {
                continue;
            }
            kept.add(new Kept(row, center, SourceValues.fit(row.positionCode(), 80)));
        }
        return kept;
    }

    /**
     * Active positions held by an included person who is not in a MyHR center.
     * Those parents are occupied, so they are not treated as vacant.
     */
    private static Set<String> occupiedOutside(
            Iterable<PersonRow> rows,
            Set<String> myhrCenters,
            Map<String, PositionRow> activePositions) {
        Set<String> centers = myhrCenters == null ? Set.of() : myhrCenters;
        Set<String> ids = new LinkedHashSet<>();
        for (PersonRow row : rows) {
            if (!includedStatus(row.status())) {
                continue;
            }
            String center = centerOf(row.companyCountry());
            if (center != null && centers.contains(center)) {
                continue;
            }
            String code = SourceValues.fit(row.positionCode(), 80);
            if (code != null && activePositions.containsKey(code)) {
                ids.add(code);
            }
        }
        return ids;
    }

    private static boolean chainPosition(
            String positionId,
            Map<String, List<Kept>> byPosition,
            Set<String> occupiedOutside,
            Map<String, PositionRow> activePositions) {
        return activePositions.containsKey(positionId)
                && (byPosition.containsKey(positionId) || !occupiedOutside.contains(positionId));
    }

    private static Map<String, PositionRow> activePositions(
            List<PositionRow> rows, UUID runId, List<SourceIssue> issues, Instant now) {
        Map<String, PositionRow> index = new LinkedHashMap<>();
        for (PositionRow row : rows) {
            String code = SourceValues.fit(row.positionCode(), 80);
            if (code == null || !isActive(row.status())) {
                continue;
            }
            PositionRow existing = index.get(code);
            if (existing == null) {
                index.put(code, row);
                continue;
            }
            String parent = SourceValues.fit(row.parentCode(), 80);
            String keptParent = SourceValues.fit(existing.parentCode(), 80);
            if (parent != null && keptParent != null && !parent.equals(keptParent)) {
                issues.add(SourceIssue.error(
                        runId,
                        TimesheetSyncErrorCode.DUPLICATE_ROW,
                        "Position " + code + " has more than one parent.",
                        null,
                        null,
                        code,
                        null,
                        row.sourceRow(),
                        now));
            }
        }
        return index;
    }

    private static void addRoles(
            UUID runId,
            Instant now,
            List<SourceIssue> issues,
            Map<String, SeatDraft> seats,
            Kept person,
            Set<String> supervisors,
            Set<String> managers) {
        if (isZero(person.row.directReports())) {
            addSeat(seats, person, "AGENT");
        } else if (person.row.directReports() != null && SourceValues.decimal(person.row.directReports()) == null) {
            issues.add(issue(
                    runId, now, TimesheetSyncErrorCode.MISSING_FIELD, "Direct Reports is not a number.", person));
        }
        if (supervisors.contains(person.positionCode)) {
            addSeat(seats, person, "SUPERVISOR");
        }
        if (managers.contains(person.positionCode)) {
            addSeat(seats, person, "SR_MANAGER");
        }
    }

    private static List<OrgPositionParent> edges(
            UUID runId,
            Instant now,
            List<SourceIssue> issues,
            Map<String, SeatDraft> seats,
            Map<String, List<Kept>> byPosition,
            Map<String, PositionRow> positions,
            Set<String> supervisors,
            Set<String> managers) {
        List<OrgPositionParent> edges = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (SeatDraft seat : seats.values()) {
            if ("AGENT".equals(seat.roleType)) {
                addEdge(
                        runId, now, issues, edges, seen, seats, byPosition, positions,
                        supervisors, managers, seat, "SUPERVISOR");
            } else if ("SUPERVISOR".equals(seat.roleType)) {
                addEdge(
                        runId, now, issues, edges, seen, seats, byPosition, positions,
                        supervisors, managers, seat, "SR_MANAGER");
            }
        }
        for (String supervisor : supervisors) {
            if (byPosition.containsKey(supervisor)) {
                continue;
            }
            PositionRow row = positions.get(supervisor);
            if (row == null) {
                continue;
            }
            addEdge(
                    runId, now, issues, edges, seen, seats, byPosition, positions,
                    supervisors, managers,
                    new SeatDraft(null, supervisor, "SUPERVISOR", row.sourceRow()),
                    "SR_MANAGER");
        }
        return edges;
    }

    private static void addEdge(
            UUID runId,
            Instant now,
            List<SourceIssue> issues,
            List<OrgPositionParent> edges,
            Set<String> seen,
            Map<String, SeatDraft> seats,
            Map<String, List<Kept>> byPosition,
            Map<String, PositionRow> positions,
            Set<String> supervisors,
            Set<String> managers,
            SeatDraft seat,
            String parentRole) {
        String parent = parentCode(positions, seat.positionId);
        if (parent == null) {
            issues.add(SourceIssue.error(
                    runId,
                    TimesheetSyncErrorCode.MISSING_PARENT,
                    "Position " + seat.positionId + " has no parent position.",
                    null,
                    seat.ccgid,
                    seat.positionId,
                    null,
                    seat.sourceRow,
                    now));
            return;
        }
        List<Kept> occupants = byPosition.getOrDefault(parent, List.of());
        if (occupants.isEmpty()) {
            issues.add(SourceIssue.error(
                    runId,
                    TimesheetSyncErrorCode.MISSING_PARENT,
                    "Parent position " + parent + " has no occupant in a MyHR + SKPI center.",
                    null,
                    seat.ccgid,
                    parent,
                    null,
                    seat.sourceRow,
                    now));
            if (isChainParent(parent, parentRole, supervisors, managers)) {
                remember(runId, edges, seen, seat, parent, parentRole);
            }
            return;
        }
        if (occupants.size() > 1) {
            issues.add(SourceIssue.error(
                    runId,
                    TimesheetSyncErrorCode.OCCUPANCY_CONFLICT,
                    "Parent position " + parent + " has more than one occupant.",
                    null,
                    seat.ccgid,
                    parent,
                    null,
                    seat.sourceRow,
                    now));
            return;
        }
        String parentCcgid = occupants.getFirst().ccgid;
        if (!seats.containsKey(seatKey(parentCcgid, parent, parentRole))) {
            return;
        }
        remember(runId, edges, seen, seat, parent, parentRole);
    }

    private static boolean isChainParent(
            String parent, String parentRole, Set<String> supervisors, Set<String> managers) {
        return ("SUPERVISOR".equals(parentRole) && supervisors.contains(parent))
                || ("SR_MANAGER".equals(parentRole) && managers.contains(parent));
    }

    private static void remember(
            UUID runId,
            List<OrgPositionParent> edges,
            Set<String> seen,
            SeatDraft seat,
            String parent,
            String parentRole) {
        String key = seat.positionId + "|" + seat.roleType + "|" + parent + "|" + parentRole;
        if (seen.add(key)) {
            edges.add(OrgPositionParent.create(runId, seat.positionId, seat.roleType, parent, parentRole));
        }
    }

    private static String parentCode(Map<String, PositionRow> positions, String positionCode) {
        PositionRow position = positions.get(positionCode);
        if (position == null) {
            return null;
        }
        String parent = SourceValues.fit(position.parentCode(), 80);
        if (parent == null || parent.equals(positionCode)) {
            return null;
        }
        return parent;
    }

    private static List<OrgPerson> peopleOf(UUID runId, List<Kept> people) {
        List<OrgPerson> rows = new ArrayList<>();
        Set<String> empIds = new LinkedHashSet<>();
        for (Kept person : people) {
            String empId = SourceValues.fit(person.row.personId(), 80);
            if (empId != null && !empIds.add(empId)) {
                empId = null;
            }
            String name = SourceValues.fit(
                    ((person.row.lastName() == null ? "" : person.row.lastName())
                            + " "
                            + (person.row.firstName() == null ? "" : person.row.firstName())),
                    200);
            if (name == null) {
                name = person.ccgid;
            }
            OrgPerson row = OrgPerson.create(
                    runId,
                    person.ccgid,
                    empId,
                    person.center,
                    name,
                    SourceValues.fit(person.row.email(), 254),
                    SourceValues.fit(person.row.jobRole(), 200));
            rows.add(row);
        }
        return rows;
    }

    private static List<OrgPosition> positionsOf(
            UUID runId,
            Iterable<SeatDraft> seats,
            Set<String> supervisors,
            Set<String> managers,
            Map<String, List<Kept>> byPosition) {
        Map<String, OrgPosition> positions = new LinkedHashMap<>();
        for (SeatDraft seat : seats) {
            positions.putIfAbsent(
                    seat.positionId + "|" + seat.roleType,
                    OrgPosition.create(runId, seat.positionId, seat.roleType));
        }
        addVacant(runId, positions, supervisors, "SUPERVISOR", byPosition);
        addVacant(runId, positions, managers, "SR_MANAGER", byPosition);
        return List.copyOf(positions.values());
    }

    private static void addVacant(
            UUID runId,
            Map<String, OrgPosition> positions,
            Set<String> positionIds,
            String role,
            Map<String, List<Kept>> byPosition) {
        for (String positionId : positionIds) {
            if (byPosition.containsKey(positionId)) {
                continue;
            }
            positions.putIfAbsent(
                    positionId + "|" + role, OrgPosition.create(runId, positionId, role));
        }
    }

    private static void addSeat(Map<String, SeatDraft> seats, Kept person, String role) {
        seats.putIfAbsent(
                seatKey(person.ccgid, person.positionCode, role),
                new SeatDraft(person.ccgid, person.positionCode, role, person.row.sourceRow()));
    }

    private static String seatKey(String ccgid, String positionId, String role) {
        return ccgid + "|" + positionId + "|" + role;
    }

    private static SourceIssue issue(
            UUID runId, Instant now, TimesheetSyncErrorCode code, String message, Kept person) {
        return SourceIssue.error(
                runId,
                code,
                message,
                SourceValues.fit(person.row.personId(), 80),
                person.ccgid,
                person.positionCode,
                null,
                person.row.sourceRow(),
                now);
    }

    static String centerOf(String companyCountry) {
        String fitted = SourceValues.fit(companyCountry, 80);
        if (fitted == null) {
            return null;
        }
        String prefix = fitted.split("-", 2)[0].trim().toUpperCase(Locale.ROOT);
        return COUNTRY_CENTER.get(prefix);
    }

    private static boolean includedStatus(String status) {
        String value = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        return value.equals("active") || value.equals("paid leave") || value.equals("unpaid leave");
    }

    private static boolean isActive(String status) {
        return status != null && status.trim().equalsIgnoreCase("A");
    }

    private static boolean isZero(String directReports) {
        var number = SourceValues.decimal(directReports);
        return number != null && number.signum() == 0;
    }

    private static final class Kept {
        private final PersonRow row;
        private final String center;
        private final String positionCode;
        private final String ccgid;

        private Kept(PersonRow row, String center, String positionCode) {
            this.row = row;
            this.center = center;
            this.positionCode = positionCode;
            this.ccgid = SourceValues.fit(row.username(), 32);
        }
    }

    private record SeatDraft(String ccgid, String positionId, String roleType, int sourceRow) {
    }
}
