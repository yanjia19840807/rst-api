package com.cmacgm.gbs.rst.api.exercise.associateddata.persistence;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.ExerciseTeamSetup;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for Exercise Team Setup. */
public interface ExerciseTeamSetupRepository extends JpaRepository<ExerciseTeamSetup, UUID> {
}
