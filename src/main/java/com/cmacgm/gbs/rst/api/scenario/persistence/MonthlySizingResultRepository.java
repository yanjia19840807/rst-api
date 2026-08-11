package com.cmacgm.gbs.rst.api.scenario.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.scenario.domain.MonthlySizingResult;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for monthly sizing results. */
public interface MonthlySizingResultRepository extends JpaRepository<MonthlySizingResult, UUID> {

    /**
     * Lists results for a simulation run.
     *
     * @param simulationRunId simulation run id
     * @return results
     */
    List<MonthlySizingResult> findBySimulationRunId(UUID simulationRunId);

    /**
     * Deletes all monthly sizing rows for a simulation run.
     */
    void deleteBySimulationRunId(UUID simulationRunId);
}
