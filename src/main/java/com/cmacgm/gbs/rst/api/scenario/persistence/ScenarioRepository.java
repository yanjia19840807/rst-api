package com.cmacgm.gbs.rst.api.scenario.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for scenarios. */
public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {

    /**
     * Lists non-deleted scenarios for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return scenarios
     */
    List<Scenario> findByExerciseIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID exerciseId);

    /**
     * Loads scenarios with assumptions for repository allocation.
     *
     * @param ids scenario ids
     * @return scenarios
     */
    @EntityGraph(attributePaths = "assumptions")
    @Query("select s from Scenario s where s.id in :ids")
    List<Scenario> findWithAssumptionsByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Finds a non-deleted scenario owned by an Exercise.
     *
     * @param id scenario id
     * @param exerciseId Exercise id
     * @return optional scenario
     */
    Optional<Scenario> findByIdAndExerciseIdAndDeletedAtIsNull(UUID id, UUID exerciseId);

    /**
     * Finds the official scenario for an Exercise if present.
     *
     * @param exerciseId Exercise id
     * @return optional official scenario
     */
    Optional<Scenario> findByExerciseIdAndStatusAndDeletedAtIsNull(UUID exerciseId, String status);

    /**
     * Returns whether an active (non-deleted) scenario code already exists for an Exercise.
     */
    boolean existsByExerciseIdAndScenarioCodeAndDeletedAtIsNull(UUID exerciseId, String scenarioCode);

    /**
     * Lists all scenario codes for an Exercise (including soft-deleted) for next-code allocation.
     */
    @Query("select s.scenarioCode from Scenario s where s.exerciseId = :exerciseId")
    List<String> findScenarioCodesByExerciseId(@Param("exerciseId") UUID exerciseId);
}
