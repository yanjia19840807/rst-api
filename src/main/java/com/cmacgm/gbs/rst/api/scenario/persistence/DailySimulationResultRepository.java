package com.cmacgm.gbs.rst.api.scenario.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.scenario.domain.DailySimulationResult;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for daily simulation results. */
public interface DailySimulationResultRepository extends JpaRepository<DailySimulationResult, UUID> {

    /**
     * Lists results for a simulation run ordered by date.
     *
     * @param simulationRunId simulation run id
     * @return results
     */
    List<DailySimulationResult> findBySimulationRunIdOrderByResultDateAsc(UUID simulationRunId);

    /**
     * Deletes all daily simulation rows for a simulation run.
     */
    void deleteBySimulationRunId(UUID simulationRunId);
}
