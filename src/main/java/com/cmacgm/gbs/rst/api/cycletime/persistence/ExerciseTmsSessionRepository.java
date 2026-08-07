package com.cmacgm.gbs.rst.api.cycletime.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.cycletime.domain.ExerciseTmsSession;
import com.cmacgm.gbs.rst.api.cycletime.domain.ExerciseTmsSession.Pk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for Exercise TMS session selections. */
public interface ExerciseTmsSessionRepository extends JpaRepository<ExerciseTmsSession, Pk> {

    /**
     * Lists TMS selections for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return selections
     */
    List<ExerciseTmsSession> findByExerciseId(UUID exerciseId);

    /**
     * Deletes all TMS selections for an Exercise.
     *
     * @param exerciseId Exercise id
     */
    @Modifying
    @Query("delete from ExerciseTmsSession e where e.exerciseId = :exerciseId")
    void deleteByExerciseId(@Param("exerciseId") UUID exerciseId);
}
