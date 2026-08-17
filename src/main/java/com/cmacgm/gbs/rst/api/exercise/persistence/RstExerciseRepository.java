package com.cmacgm.gbs.rst.api.exercise.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * @param ownerCcgid owner Supervisor ccgid
     * @return optional Exercise
     */
    @EntityGraph(attributePaths = {"toolkitSnapshot"})
    Optional<RstExercise> findByIdAndOwnerCcgidAndDeletedAtIsNull(UUID id, String ownerCcgid);

    /**
     * Finds a non-deleted Exercise by id (owner or approver read path).
     *
     * @param id Exercise id
     * @return optional Exercise
     */
    @EntityGraph(attributePaths = {"toolkitSnapshot"})
    Optional<RstExercise> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Lists non-deleted Exercises for a Supervisor ordered by recent update.
     *
     * @param ownerCcgid owner Supervisor ccgid
     * @return exercises
     */
    @EntityGraph(attributePaths = {"toolkitSnapshot"})
    List<RstExercise> findByOwnerCcgidAndDeletedAtIsNullOrderByUpdatedAtDescIdAsc(String ownerCcgid);

    /**
     * Returns whether any Exercise references the Toolkit.
     *
     * @param toolkitId Toolkit id
     * @return true when referenced
     */
    boolean existsByToolkitId(UUID toolkitId);

    /**
     * Archived/validated Exercises for a Toolkit, newest first (use Pageable for top-N).
     */
    @EntityGraph(attributePaths = {"toolkitSnapshot"})
    @Query("""
            select e from RstExercise e
            where e.toolkitId = :toolkitId
              and e.deletedAt is null
              and e.workflowStatus in :statuses
            order by coalesce(e.validatedAt, e.updatedAt) desc, e.updatedAt desc, e.id desc
            """)
    List<RstExercise> findArchivedByToolkit(
            @Param("toolkitId") UUID toolkitId,
            @Param("statuses") Collection<String> statuses,
            Pageable pageable);

    /**
     * APPROVED Exercises for RST Repository, with Toolkit snapshot and Shared KPI lines.
     *
     * @return approved exercises
     */
    @EntityGraph(attributePaths = {"toolkitSnapshot", "sharedKpiLines"})
    @Query("""
            select e from RstExercise e
            where e.deletedAt is null
              and e.workflowStatus = 'APPROVED'
            order by e.submittedAt desc, e.exerciseCode asc, e.id asc
            """)
    List<RstExercise> findApprovedRepositoryExercises();

    /**
     * APPROVED Exercises for Support Repository, with Toolkit snapshot only.
     *
     * @return approved exercises
     */
    @EntityGraph(attributePaths = {"toolkitSnapshot"})
    @Query("""
            select e from RstExercise e
            where e.deletedAt is null
              and e.workflowStatus = 'APPROVED'
            order by e.submittedAt desc, e.exerciseCode asc, e.id asc
            """)
    List<RstExercise> findApprovedSupportRepositoryExercises();

    /**
     * UNDER_REVIEW Exercises for Validation Workflow, with Toolkit snapshot and Shared KPI lines.
     *
     * @return in-review exercises
     */
    @EntityGraph(attributePaths = {"toolkitSnapshot", "sharedKpiLines"})
    @Query("""
            select e from RstExercise e
            where e.deletedAt is null
              and e.workflowStatus = 'UNDER_REVIEW'
            order by e.submittedAt desc, e.exerciseCode asc, e.id asc
            """)
    List<RstExercise> findUnderReviewValidationExercises();

    /**
     * Counts non-deleted Exercises in one workflow status.
     *
     * @param workflowStatus workflow status
     * @return count
     */
    long countByDeletedAtIsNullAndWorkflowStatus(String workflowStatus);
}
