package com.cmacgm.gbs.rst.api.scenario.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.scenario.domain.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for validation findings. */
public interface ValidationResultRepository extends JpaRepository<ValidationResult, UUID> {

    /**
     * Lists findings for an Exercise and stage ordered by evaluation time.
     *
     * @param exerciseId Exercise id
     * @param validationStage stage
     * @return findings
     */
    List<ValidationResult> findByExerciseIdAndValidationStageOrderByEvaluatedAtDesc(
            UUID exerciseId, String validationStage);
}
