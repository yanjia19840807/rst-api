package com.cmacgm.gbs.rst.api.scenario.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.scenario.domain.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;

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
     * Returns whether a scenario code already exists for an Exercise.
     *
     * @param exerciseId Exercise id
     * @param scenarioCode scenario code
     * @return true when present (including soft-deleted rows that still occupy the unique key)
     */
    boolean existsByExerciseIdAndScenarioCode(UUID exerciseId, String scenarioCode);
}
