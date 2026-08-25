package com.cmacgm.gbs.rst.api.exercise.scenario.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.scenario.domain.ForecastRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for forecast runs. */
public interface ForecastRunRepository extends JpaRepository<ForecastRun, UUID> {

    /**
     * Lists all forecast runs for a scenario.
     */
    List<ForecastRun> findByScenarioId(UUID scenarioId);

    /**
     * Returns the max run number for a scenario.
     *
     * @param scenarioId scenario id
     * @return max run number or empty
     */
    @Query("select max(f.runNo) from ForecastRun f where f.scenarioId = :scenarioId")
    Optional<Integer> findMaxRunNo(@Param("scenarioId") UUID scenarioId);

    /**
     * Finds the latest ACCEPTED forecast for a scenario.
     *
     * @param scenarioId scenario id
     * @return optional forecast
     */
    Optional<ForecastRun> findFirstByScenarioIdAndStatusOrderByRunNoDesc(UUID scenarioId, String status);

    /**
     * Finds the latest ACCEPTED forecast for a scenario at a given level.
     *
     * @param scenarioId scenario id
     * @param forecastLevel MONTHLY or DAILY
     * @param status run status
     * @return optional forecast
     */
    Optional<ForecastRun> findFirstByScenarioIdAndForecastLevelAndStatusOrderByRunNoDesc(
            UUID scenarioId, String forecastLevel, String status);
}
