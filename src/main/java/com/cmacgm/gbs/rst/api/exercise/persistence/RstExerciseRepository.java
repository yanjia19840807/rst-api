package com.cmacgm.gbs.rst.api.exercise.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for Exercise aggregates with snapshot graphs.
 */
public interface RstExerciseRepository extends JpaRepository<RstExercise, UUID> {

    /**
     * Finds a non-deleted Exercise owned by a Supervisor.
     * Loads Toolkit snapshot only; Subtasks / Shared KPI lines are fetched lazily in-transaction
     * to avoid Hibernate {@code MultipleBagFetchException}.
     *
     * @param id Exercise id
     * @param ownerUserId owner Supervisor id
     * @return optional Exercise
     */
    @EntityGraph(attributePaths = {"toolkitSnapshot"})
    Optional<RstExercise> findByIdAndOwnerUserIdAndDeletedAtIsNull(UUID id, UUID ownerUserId);

    /**
     * Lists non-deleted Exercises for a Supervisor ordered by recent update.
     *
     * @param ownerUserId owner Supervisor id
     * @return exercises
     */
    @EntityGraph(attributePaths = {"toolkitSnapshot"})
    List<RstExercise> findByOwnerUserIdAndDeletedAtIsNullOrderByUpdatedAtDescIdAsc(UUID ownerUserId);

    /**
     * Returns whether any Exercise references the Toolkit.
     *
     * @param toolkitId Toolkit id
     * @return true when referenced
     */
    boolean existsByToolkitId(UUID toolkitId);
}
