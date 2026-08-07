package com.cmacgm.gbs.rst.api.cycletime.persistence;

import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for cycle-time baselines. */
public interface CycleTimeBaselineRepository extends JpaRepository<CycleTimeBaseline, UUID> {

    /**
     * Finds the active baseline for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return optional active baseline
     */
    Optional<CycleTimeBaseline> findByExerciseIdAndActiveTrue(UUID exerciseId);
}
