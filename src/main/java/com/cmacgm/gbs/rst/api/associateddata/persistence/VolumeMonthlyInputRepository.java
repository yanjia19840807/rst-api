package com.cmacgm.gbs.rst.api.associateddata.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.VolumeMonthlyInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for monthly volume inputs. */
public interface VolumeMonthlyInputRepository extends JpaRepository<VolumeMonthlyInput, UUID> {

    /**
     * Lists monthly volumes for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return monthly rows
     */
    List<VolumeMonthlyInput> findByExerciseIdOrderByMonthAsc(UUID exerciseId);

    /**
     * Deletes all monthly volumes for an Exercise.
     *
     * @param exerciseId Exercise id
     */
    @Modifying
    @Query("delete from VolumeMonthlyInput v where v.exerciseId = :exerciseId")
    void deleteByExerciseId(@Param("exerciseId") UUID exerciseId);
}
