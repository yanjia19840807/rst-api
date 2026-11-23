package com.cmacgm.gbs.rst.api.exercise.associateddata.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseVolumeDailyInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for daily volume inputs. */
public interface ExerciseVolumeDailyInputRepository extends JpaRepository<ExerciseVolumeDailyInput, UUID> {

    /**
     * Lists daily volumes for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return daily rows
     */
    List<ExerciseVolumeDailyInput> findByExerciseIdOrderByVolumeDateAsc(UUID exerciseId);

    /**
     * Whether this Exercise has at least one Daily Actual Volume.
     * Toolkit daily history does not count.
     *
     * @param exerciseId Exercise id
     * @return true when a saved daily row has an actual
     */
    default boolean hasActualVolume(UUID exerciseId) {
        return findByExerciseIdOrderByVolumeDateAsc(exerciseId).stream()
                .anyMatch(row -> row.getActualVolume() != null);
    }

    /**
     * Deletes all daily volumes for an Exercise.
     *
     * @param exerciseId Exercise id
     */
    @Modifying
    @Query("delete from ExerciseVolumeDailyInput v where v.exerciseId = :exerciseId")
    void deleteByExerciseId(@Param("exerciseId") UUID exerciseId);
}
