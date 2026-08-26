package com.cmacgm.gbs.rst.api.exercise.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.domain.RstExercise;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
     * APPROVED Exercises for RST Repository, with Toolkit snapshot and Shared KPI lines.
     *
     * @return approved exercises
     */
    @EntityGraph(attributePaths = {"toolkitSnapshot", "sharedKpiLines"})
    @Query("""
            select e from RstExercise e
            where e.deletedAt is null
              and exists (
                  select 1 from ProcessInstance w
                  join w.tasks t
                  where w.exerciseId = e.id
                    and w.status = com.cmacgm.gbs.rst.api.workflow.domain.ProcessStatus.FINISHED
                    and t.node = com.cmacgm.gbs.rst.api.workflow.domain.TaskNode.LTH
                    and t.status = com.cmacgm.gbs.rst.api.workflow.domain.TaskStatus.APPROVED
              )
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
              and exists (
                  select 1 from ProcessInstance w
                  join w.tasks t
                  where w.exerciseId = e.id
                    and w.status = com.cmacgm.gbs.rst.api.workflow.domain.ProcessStatus.FINISHED
                    and t.node = com.cmacgm.gbs.rst.api.workflow.domain.TaskNode.LTH
                    and t.status = com.cmacgm.gbs.rst.api.workflow.domain.TaskStatus.APPROVED
              )
            order by e.submittedAt desc, e.exerciseCode asc, e.id asc
            """)
    List<RstExercise> findApprovedSupportRepositoryExercises();

    /**
     * Processes waiting on a reviewer, with Toolkit snapshot and Shared KPI lines.
     *
     * @return in-review exercises
     */
    @EntityGraph(attributePaths = {"toolkitSnapshot", "sharedKpiLines"})
    @Query("""
            select e from RstExercise e
            where e.deletedAt is null
              and exists (
                  select 1 from ProcessInstance w
                  join w.tasks t
                  where w.exerciseId = e.id
                    and t.status = com.cmacgm.gbs.rst.api.workflow.domain.TaskStatus.PENDING
                    and t.node in (
                        com.cmacgm.gbs.rst.api.workflow.domain.TaskNode.MANAGER,
                        com.cmacgm.gbs.rst.api.workflow.domain.TaskNode.CDH,
                        com.cmacgm.gbs.rst.api.workflow.domain.TaskNode.LTH
                    )
              )
            order by e.submittedAt desc, e.exerciseCode asc, e.id asc
            """)
    List<RstExercise> findUnderReviewValidationExercises();

    /**
     * Counts Exercises whose process is waiting on a reviewer.
     *
     * @return under-review count
     */
    @Query("""
            select count(e) from RstExercise e
            where e.deletedAt is null
              and exists (
                  select 1 from ProcessInstance w
                  join w.tasks t
                  where w.exerciseId = e.id
                    and t.status = com.cmacgm.gbs.rst.api.workflow.domain.TaskStatus.PENDING
                    and t.node in (
                        com.cmacgm.gbs.rst.api.workflow.domain.TaskNode.MANAGER,
                        com.cmacgm.gbs.rst.api.workflow.domain.TaskNode.CDH,
                        com.cmacgm.gbs.rst.api.workflow.domain.TaskNode.LTH
                    )
              )
            """)
    long countUnderReview();
}
