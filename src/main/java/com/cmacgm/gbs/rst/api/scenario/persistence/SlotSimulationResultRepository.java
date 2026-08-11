package com.cmacgm.gbs.rst.api.scenario.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.scenario.domain.SlotSimulationResult;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for slot simulation results. */
public interface SlotSimulationResultRepository extends JpaRepository<SlotSimulationResult, UUID> {

    /**
     * Lists results for a simulation run.
     *
     * @param simulationRunId simulation run id
     * @return results
     */
    List<SlotSimulationResult> findBySimulationRunId(UUID simulationRunId);

    /**
     * Deletes all slot simulation rows for a simulation run.
     */
    void deleteBySimulationRunId(UUID simulationRunId);
}
