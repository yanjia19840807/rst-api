package com.cmacgm.gbs.rst.api.associateddata.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseVolumeSlotInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for slot volume inputs. */
public interface ExerciseVolumeSlotInputRepository extends JpaRepository<ExerciseVolumeSlotInput, UUID> {

    /**
     * Lists slot volumes for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return slot rows
     */
    List<ExerciseVolumeSlotInput> findByExerciseIdOrderBySlotStartAtAsc(UUID exerciseId);

    /**
     * Deletes all slot volumes for an Exercise.
     *
     * @param exerciseId Exercise id
     */
    @Modifying
    @Query("delete from ExerciseVolumeSlotInput v where v.exerciseId = :exerciseId")
    void deleteByExerciseId(@Param("exerciseId") UUID exerciseId);
}
