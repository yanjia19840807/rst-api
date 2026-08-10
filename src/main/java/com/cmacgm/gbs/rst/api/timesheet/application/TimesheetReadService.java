package com.cmacgm.gbs.rst.api.timesheet.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSnapshotRowRepository;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimesheetReadService {

    private final TimesheetSyncRunRepository syncRuns;
    private final TimesheetSnapshotRowRepository snapshotRows;

    public TimesheetReadService(
            TimesheetSyncRunRepository syncRuns,
            TimesheetSnapshotRowRepository snapshotRows) {
        this.syncRuns = syncRuns;
        this.snapshotRows = snapshotRows;
    }

    @Transactional(readOnly = true)
    public ActiveSnapshot activeSnapshot() {
        var run = syncRuns.findByStatus("ACTIVE")
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "active-timesheet-missing",
                        "No ACTIVE Timesheet snapshot is available."));
        return new ActiveSnapshot(
                run.getId(),
                run.getSyncDate(),
                run.getRowCount() == null ? 0 : run.getRowCount());
    }

    @Transactional(readOnly = true)
    public List<HierarchyCandidate> supervisorHierarchy(String supervisorCcgid) {
        return snapshotRows.findDistinctHierarchyBySupervisorCcgid(supervisorCcgid).stream()
                .map(row -> new HierarchyCandidate(
                        row.getSupervisorPositionId(),
                        row.getCenter(),
                        row.getDomain(),
                        row.getPl1(),
                        row.getPl2(),
                        row.getPl3Code(),
                        row.getPl3Name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> countries(String supervisorPositionId, String pl3Code) {
        return snapshotRows.findDistinctCountries(supervisorPositionId, pl3Code);
    }

    @Transactional(readOnly = true)
    public List<KpiCandidate> kpis(
            String supervisorPositionId, String pl3Code, List<String> countries) {
        if (countries == null || countries.isEmpty()) {
            return List.of();
        }
        return snapshotRows.aggregateKpis(supervisorPositionId, pl3Code, countries).stream()
                .map(row -> new KpiCandidate(
                        row.getCarrier(),
                        row.getSite(),
                        row.getCustomerCountry(),
                        row.getDeliveryHc()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal headcount(
            String supervisorPositionId,
            String pl3Code,
            String carrier,
            String site,
            String country) {
        BigDecimal total = snapshotRows.sumHeadcount(
                supervisorPositionId, pl3Code, carrier, site, country);
        return total == null ? BigDecimal.ZERO : total;
    }

    @Transactional(readOnly = true)
    public boolean supervisorOwnsScope(
            String ccgid, String supervisorPositionId, String pl3Code) {
        return snapshotRows.existsActiveScopeForSupervisor(ccgid, supervisorPositionId, pl3Code);
    }

    @Transactional(readOnly = true)
    public boolean agentCanUse(
            String ccgid, String supervisorPositionId, String pl3Code) {
        return snapshotRows.existsActiveScopeForAgent(ccgid, supervisorPositionId, pl3Code);
    }

    /**
     * Lists distinct team agents under a supervisor from the ACTIVE Timesheet snapshot.
     *
     * @param supervisorCcgid supervisor CCGID
     * @return agents ordered by display name
     */
    @Transactional(readOnly = true)
    public List<TeamAgent> teamAgents(String supervisorCcgid) {
        return snapshotRows.findDistinctAgentsBySupervisorCcgid(supervisorCcgid).stream()
                .map(row -> new TeamAgent(
                        row.getEmpCcgid(),
                        row.getEmpName() == null || row.getEmpName().isBlank()
                                ? row.getEmpCcgid()
                                : row.getEmpName()))
                .toList();
    }

    public record ActiveSnapshot(UUID id, LocalDate syncDate, int rowCount) {
    }

    /** Team agent option for Supervisor TMS filters. */
    public record TeamAgent(String ccgid, String name) {
    }

    public record HierarchyCandidate(
            String supervisorPositionId,
            String center,
            String domain,
            String pl1,
            String pl2,
            String pl3Code,
            String pl3Name) {
    }

    public record KpiCandidate(
            String carrier, String site, String customerCountry, BigDecimal deliveryHc) {
    }
}
