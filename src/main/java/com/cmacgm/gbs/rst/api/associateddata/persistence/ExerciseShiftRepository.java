package com.cmacgm.gbs.rst.api.associateddata.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseShift;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for Exercise shifts. */
public interface ExerciseShiftRepository extends JpaRepository<ExerciseShift, UUID> {

    /**
     * Lists active shifts for an Exercise ordered by shift number.
     *
     * @param exerciseId Exercise id
     * @return active shifts
     */
    List<ExerciseShift> findByExerciseIdAndDeletedAtIsNullOrderByShiftNoAsc(UUID exerciseId);
}
