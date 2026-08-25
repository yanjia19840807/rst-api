package com.cmacgm.gbs.rst.api.exercise.scenario.persistence;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.scenario.domain.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for validation findings. */
public interface ValidationResultRepository extends JpaRepository<ValidationResult, UUID> {
}
