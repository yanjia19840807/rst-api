package com.cmacgm.gbs.rst.api.sourcedata.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterDeliveryHc;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterSource;
import com.cmacgm.gbs.rst.api.sourcedata.persistence.CenterDeliveryHcRepository;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPerson;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPosition;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPositionParent;
import com.cmacgm.gbs.rst.api.timesheet.domain.OrgSeat;
import com.cmacgm.gbs.rst.api.timesheet.domain.ProcessScope;
import com.cmacgm.gbs.rst.api.timesheet.domain.SourceSnapshot;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPersonRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionParentRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgPositionRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgSeatRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.OrgSeatRepository.OccupancyRow;
import com.cmacgm.gbs.rst.api.timesheet.persistence.ProcessScopeRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.SourceSnapshotRepository;

/**
 * Reads the current mapped result for one center. Timesheet centers use the
 * existing Daily and Monthly browse. MyHR centers are read here.
 */
@Service
public class CurrentDataService {

    private static final String ACTIVE = "ACTIVE";

    private final CenterSourceService centers;
    private final SourceSnapshotRepository snapshots;
    private final OrgPersonRepository people;
    private final OrgPositionRepository positions;
    private final OrgPositionParentRepository parents;
    private final OrgSeatRepository seats;
    private final ProcessScopeRepository processes;
    private final CenterDeliveryHcRepository deliveryHc;

    /**
     * @param centers center source mode
     * @param snapshots snapshot headers
     * @param people MyHR people
     * @param positions MyHR positions
     * @param parents MyHR parent edges
     * @param seats MyHR seats
     * @param processes SKPI process scope
     * @param deliveryHc SKPI Delivery HC
     */
    public CurrentDataService(
            CenterSourceService centers,
            SourceSnapshotRepository snapshots,
            OrgPersonRepository people,
            OrgPositionRepository positions,
            OrgPositionParentRepository parents,
            OrgSeatRepository seats,
            ProcessScopeRepository processes,
            CenterDeliveryHcRepository deliveryHc) {
        this.centers = centers;
        this.snapshots = snapshots;
        this.people = people;
        this.positions = positions;
        this.parents = parents;
        this.seats = seats;
        this.processes = processes;
        this.deliveryHc = deliveryHc;
    }

    /**
     * @return the current MyHR and SKPI files
     */
    @Transactional(readOnly = true)
    public List<SourceFileView> activeInterim() {
        return List.of(file("MYHR", ""), file("SHARED_KPI", ""));
    }

    /**
     * @param center GBS center
     * @return source mode and the files that produced the current rows
     */
    @Transactional(readOnly = true)
    public CurrentView current(String center) {
        CenterSource source = centers.require(center);
        List<SourceFileView> files = CenterSource.TIMESHEET.equals(source.getSourceMode())
                ? List.of(file("DAILY", source.getCenter()), file("MONTHLY", source.getCenter()))
                : List.of(file("MYHR", ""), file("SHARED_KPI", ""));
        return new CurrentView(source.getCenter(), source.getSourceMode(), files);
    }

    /**
     * @param center GBS center
     * @param q search text
     * @param page 1-based page
     * @param pageSize page size
     * @return people in the current MyHR snapshot
     */
    @Transactional(readOnly = true)
    public PageResponse<PersonView> people(String center, String q, int page, int pageSize) {
        String canonical = centers.require(center).getCenter();
        Page<OrgPerson> rows = people.searchActiveMyHr(canonical, blank(q), pageOf(page, pageSize));
        Map<String, List<OrgSeat>> seatsByPerson = seatsByPerson(rows.getContent());
        return PageResponse.from(rows, row -> toPerson(row, seatsByPerson));
    }

    /**
     * @param center GBS center
     * @param q search text
     * @param page 1-based page
     * @param pageSize page size
     * @return positions occupied by people of this center
     */
    @Transactional(readOnly = true)
    public PageResponse<PositionView> positions(String center, String q, int page, int pageSize) {
        String canonical = centers.require(center).getCenter();
        Page<OrgPosition> rows = positions.searchActiveMyHr(canonical, blank(q), pageOf(page, pageSize));
        Map<String, List<OrgPositionParent>> edges = parentsByNode(rows.getContent());
        Map<String, String> names = occupantNames(canonical, rows.getContent());
        return PageResponse.from(rows, row -> toPosition(row, canonical, edges, names));
    }

    /**
     * @param center GBS center
     * @param q search text
     * @param page 1-based page
     * @param pageSize page size
     * @return seats in the current MyHR snapshot
     */
    @Transactional(readOnly = true)
    public PageResponse<SeatView> seats(String center, String q, int page, int pageSize) {
        String canonical = centers.require(center).getCenter();
        Page<OccupancyRow> rows = seats.searchActiveMyHr(canonical, blank(q), pageOf(page, pageSize));
        return PageResponse.from(rows, CurrentDataService::toSeat);
    }

    /**
     * @param center GBS center
     * @param q search text
     * @param page 1-based page
     * @param pageSize page size
     * @return SKPI processes
     */
    @Transactional(readOnly = true)
    public PageResponse<ProcessView> processes(String center, String q, int page, int pageSize) {
        String canonical = centers.require(center).getCenter();
        Page<ProcessScope> rows = processes.searchActiveShared(canonical, blank(q), pageOf(page, pageSize));
        return PageResponse.from(rows, CurrentDataService::toProcess);
    }

    /**
     * @param center GBS center
     * @param q search text
     * @param page 1-based page
     * @param pageSize page size
     * @return center Delivery HC
     */
    @Transactional(readOnly = true)
    public PageResponse<HcView> deliveryHc(String center, String q, int page, int pageSize) {
        String canonical = centers.require(center).getCenter();
        Page<CenterDeliveryHc> rows = deliveryHc.searchActive(canonical, blank(q), pageOf(page, pageSize));
        return PageResponse.from(rows, CurrentDataService::toHc);
    }

    private SourceFileView file(String kind, String center) {
        return snapshots.findByKindAndStatusAndCenter(kind, ACTIVE, center)
                .map(CurrentDataService::toFile)
                .orElseGet(() -> new SourceFileView(null, kind, null, null, null, null, false));
    }

    private static SourceFileView toFile(SourceSnapshot run) {
        String itemId = run.getSourceDriveItemId();
        return new SourceFileView(
                run.getId(),
                run.getKind(),
                run.getSourceFileName(),
                run.getSyncDate(),
                run.getPeriod(),
                run.getRowCount(),
                itemId != null && !itemId.isBlank());
    }

    private Map<String, List<OrgSeat>> seatsByPerson(List<OrgPerson> rows) {
        List<String> ids = rows.stream()
                .map(person -> person.getCcgid().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, List<OrgSeat>> grouped = new LinkedHashMap<>();
        for (OrgSeat seat : seats.findMyHrByCcgidIn(ids)) {
            grouped.computeIfAbsent(seat.getCcgid().toUpperCase(Locale.ROOT), key -> new ArrayList<>()).add(seat);
        }
        grouped.values().forEach(list -> list.sort(Comparator.comparingInt(seat -> roleOrder(seat.getRoleType()))));
        return grouped;
    }

    private PersonView toPerson(OrgPerson row, Map<String, List<OrgSeat>> seatsByPerson) {
        List<OrgSeat> held = seatsByPerson.getOrDefault(row.getCcgid().toUpperCase(Locale.ROOT), List.of());
        return new PersonView(
                row.getCcgid(),
                row.getEmpId(),
                row.getName(),
                row.getEmail(),
                row.getJobRole(),
                row.getCenter(),
                join(held.stream().map(OrgSeat::getPositionId).toList()),
                join(held.stream().map(OrgSeat::getRoleType).toList()));
    }

    private Map<String, List<OrgPositionParent>> parentsByNode(List<OrgPosition> rows) {
        List<String> ids = rows.stream().map(OrgPosition::getPositionId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, List<OrgPositionParent>> grouped = new LinkedHashMap<>();
        for (OrgPositionParent edge : parents.findMyHrByPositionIdIn(ids)) {
            grouped.computeIfAbsent(nodeKey(edge.getPositionId(), edge.getRoleType()), key -> new ArrayList<>())
                    .add(edge);
        }
        return grouped;
    }

    private Map<String, String> occupantNames(String center, List<OrgPosition> rows) {
        List<String> ids = rows.stream().map(OrgPosition::getPositionId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<OrgSeat> occupied = seats.findMyHrByPositionIdIn(center, ids);
        List<String> ccgids = occupied.stream()
                .map(seat -> seat.getCcgid().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        Map<String, String> nameByCcgid = ccgids.isEmpty()
                ? Map.of()
                : people.findMyHrByCcgidIn(ccgids).stream()
                        .collect(Collectors.toMap(
                                person -> person.getCcgid().toUpperCase(Locale.ROOT),
                                OrgPerson::getName,
                                (left, right) -> left,
                                LinkedHashMap::new));
        Map<String, List<String>> names = new LinkedHashMap<>();
        for (OrgSeat seat : occupied) {
            String name = nameByCcgid.get(seat.getCcgid().toUpperCase(Locale.ROOT));
            if (name == null || name.isBlank()) {
                continue;
            }
            names.computeIfAbsent(nodeKey(seat.getPositionId(), seat.getRoleType()), key -> new ArrayList<>())
                    .add(name);
        }
        Map<String, String> joined = new LinkedHashMap<>();
        names.forEach((key, value) -> joined.put(key, join(value)));
        return joined;
    }

    private static PositionView toPosition(
            OrgPosition row,
            String center,
            Map<String, List<OrgPositionParent>> edges,
            Map<String, String> names) {
        List<OrgPositionParent> parentsOfNode =
                edges.getOrDefault(nodeKey(row.getPositionId(), row.getRoleType()), List.of());
        return new PositionView(
                row.getPositionId(),
                row.getRoleType(),
                join(parentsOfNode.stream().map(OrgPositionParent::getParentPositionId).toList()),
                join(parentsOfNode.stream().map(OrgPositionParent::getParentRoleType).toList()),
                names.get(nodeKey(row.getPositionId(), row.getRoleType())),
                center);
    }

    private static SeatView toSeat(OccupancyRow row) {
        return new SeatView(row.getCcgid(), row.getName(), row.getPositionId(), row.getRoleType(), row.getCenter());
    }

    private static ProcessView toProcess(ProcessScope row) {
        return new ProcessView(
                row.getDomain(), row.getPl1(), row.getPl2(), row.getPl3Code(), row.getPl3Name(), row.getCenter());
    }

    private static HcView toHc(CenterDeliveryHc row) {
        return new HcView(
                row.getPl3Code(),
                row.getCarrier(),
                row.getSite(),
                row.getCustomerCountry(),
                row.getHc(),
                row.getCenter());
    }

    private static String nodeKey(String positionId, String roleType) {
        return positionId + "\n" + roleType;
    }

    private static int roleOrder(String role) {
        if ("AGENT".equals(role)) {
            return 0;
        }
        if ("SUPERVISOR".equals(role)) {
            return 1;
        }
        if ("SR_MANAGER".equals(role)) {
            return 2;
        }
        return 3;
    }

    private static String join(List<String> values) {
        String joined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        return joined.isBlank() ? null : joined;
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static PageRequest pageOf(int page, int pageSize) {
        return PageRequest.of(Math.max(1, page) - 1, Math.min(100, Math.max(1, pageSize)));
    }

    /**
     * @param center GBS center
     * @param mode TIMESHEET or MYHR_SKPI
     * @param files current source files, including an empty slot when that kind has no file
     */
    public record CurrentView(String center, String mode, List<SourceFileView> files) {
    }

    /**
     * @param id snapshot id, null when this kind has no current file
     * @param kind snapshot kind
     * @param sourceFileName original file name
     * @param syncDate business date
     * @param period SKPI period
     * @param rowCount stored rows
     * @param hasSourceFile original bytes can be downloaded
     */
    public record SourceFileView(
            UUID id,
            String kind,
            String sourceFileName,
            LocalDate syncDate,
            String period,
            Integer rowCount,
            boolean hasSourceFile) {
    }

    /**
     * @param ccgid identity
     * @param empId person id
     * @param name display name
     * @param email email
     * @param jobRole job title
     * @param center GBS center
     * @param positionId occupied positions
     * @param roleType occupied roles
     */
    public record PersonView(
            String ccgid,
            String empId,
            String name,
            String email,
            String jobRole,
            String center,
            String positionId,
            String roleType) {
    }

    /**
     * @param ccgid occupant
     * @param name occupant name
     * @param positionId position
     * @param roleType AGENT, SUPERVISOR, or SR_MANAGER
     * @param center GBS center
     */
    public record SeatView(
            String ccgid, String name, String positionId, String roleType, String center) {
    }

    /**
     * @param positionId position
     * @param roleType role
     * @param parentPositionId parent positions
     * @param parentRoleType parent roles
     * @param occupantName occupants in this center
     * @param center GBS center
     */
    public record PositionView(
            String positionId,
            String roleType,
            String parentPositionId,
            String parentRoleType,
            String occupantName,
            String center) {
    }

    /**
     * @param domain domain
     * @param pl1 process level 1
     * @param pl2 process level 2
     * @param pl3Code process level 3
     * @param pl3Name process name
     * @param center GBS center
     */
    public record ProcessView(
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name,
            String center) {
    }

    /**
     * @param pl3Code process level 3
     * @param carrier carrier
     * @param site mapped site code
     * @param customerCountry country
     * @param hc Delivery HC
     * @param center GBS center
     */
    public record HcView(
            String pl3Code,
            String carrier,
            String site,
            String customerCountry,
            BigDecimal hc,
            String center) {
    }
}
