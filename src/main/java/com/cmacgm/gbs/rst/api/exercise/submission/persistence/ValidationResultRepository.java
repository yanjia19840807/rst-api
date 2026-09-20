package com.cmacgm.gbs.rst.api.exercise.submission.persistence;

import java.util.UUID;

import java.util.List;

import com.cmacgm.gbs.rst.api.exercise.submission.domain.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for submit-time validation findings. */
public interface ValidationResultRepository extends JpaRepository<ValidationResult, UUID> {

    List<ValidationResult> findByExerciseIdOrderByEvaluatedAtAscRuleCodeAsc(UUID exerciseId);

    @Modifying
    @Query("delete from ValidationResult v where v.exerciseId = :exerciseId")
    void deleteByExerciseId(@Param("exerciseId") UUID exerciseId);
}
