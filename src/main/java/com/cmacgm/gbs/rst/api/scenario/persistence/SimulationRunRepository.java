package com.cmacgm.gbs.rst.api.scenario.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.scenario.domain.SimulationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for simulation runs. */
public interface SimulationRunRepository extends JpaRepository<SimulationRun, UUID> {

    /**
     * Lists all simulation runs for a scenario.
     */
    List<SimulationRun> findByScenarioId(UUID scenarioId);

    /**
     * Returns the max run number for a scenario and run type.
     *
     * @param scenarioId scenario id
     * @param runType run type
     * @return max run number or empty
     */
    @Query("select max(s.runNo) from SimulationRun s where s.scenarioId = :scenarioId and s.runType = :runType")
    Optional<Integer> findMaxRunNo(@Param("scenarioId") UUID scenarioId, @Param("runType") String runType);

    /**
     * Finds the latest ACCEPTED simulation of a type for a scenario.
     *
     * @param scenarioId scenario id
     * @param runType run type
     * @param status status
     * @return optional simulation
     */
    Optional<SimulationRun> findFirstByScenarioIdAndRunTypeAndStatusOrderByRunNoDesc(
            UUID scenarioId, String runType, String status);
}
