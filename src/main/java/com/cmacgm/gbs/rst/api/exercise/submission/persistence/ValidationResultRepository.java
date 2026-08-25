package com.cmacgm.gbs.rst.api.exercise.submission.persistence;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.submission.domain.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for submit-time validation findings. */
public interface ValidationResultRepository extends JpaRepository<ValidationResult, UUID> {
}
