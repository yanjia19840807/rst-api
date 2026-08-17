package com.cmacgm.gbs.rst.api.cycletime.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for cycle-time baselines. */
public interface CycleTimeBaselineRepository extends JpaRepository<CycleTimeBaseline, UUID> {

    /**
     * Finds the active baseline for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return optional active baseline
     */
    Optional<CycleTimeBaseline> findByExerciseIdAndActiveTrue(UUID exerciseId);

    /**
     * Finds active baselines for a set of Exercises.
     *
     * @param exerciseIds Exercise ids
     * @return active baselines
     */
    List<CycleTimeBaseline> findByExerciseIdInAndActiveTrue(Collection<UUID> exerciseIds);

    /**
     * Deactivates the current active baseline for an Exercise (partial unique index safe).
     *
     * @param exerciseId Exercise id
     * @return number of rows updated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CycleTimeBaseline b
               set b.active = false
             where b.exerciseId = :exerciseId
               and b.active = true
            """)
    int deactivateActiveByExerciseId(@Param("exerciseId") UUID exerciseId);
}
