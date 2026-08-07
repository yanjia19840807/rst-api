package com.cmacgm.gbs.rst.api.associateddata.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.VolumeDailyInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for daily volume inputs. */
public interface VolumeDailyInputRepository extends JpaRepository<VolumeDailyInput, UUID> {

    /**
     * Lists daily volumes for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return daily rows
     */
    List<VolumeDailyInput> findByExerciseIdOrderByVolumeDateAsc(UUID exerciseId);

    /**
     * Deletes all daily volumes for an Exercise.
     *
     * @param exerciseId Exercise id
     */
    @Modifying
    @Query("delete from VolumeDailyInput v where v.exerciseId = :exerciseId")
    void deleteByExerciseId(@Param("exerciseId") UUID exerciseId);
}
