package com.cmacgm.gbs.rst.api.exercise.associateddata.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for Exercise holidays. */
public interface ExerciseHolidayRepository extends JpaRepository<ExerciseHoliday, UUID> {

    /**
     * Lists active holidays for an Exercise ordered by date.
     *
     * @param exerciseId Exercise id
     * @return active holidays
     */
    List<ExerciseHoliday> findByExerciseIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(UUID exerciseId);
}
