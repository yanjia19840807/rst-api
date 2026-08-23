package com.cmacgm.gbs.rst.api.workflow.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for workflow instances. */
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {

    /**
     * Finds a workflow instance by submission id.
     *
     * @param submissionId submission id
     * @return optional workflow
     */
    Optional<WorkflowInstance> findBySubmissionId(UUID submissionId);

    /**
     * Finds workflow instances for the given submissions.
     *
     * @param submissionIds submission ids
     * @return workflows
     */
    List<WorkflowInstance> findBySubmissionIdIn(Collection<UUID> submissionIds);

    /**
     * ACTIVE workflows currently waiting on CDH for Toolkits in this Center × Domain.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @return workflows at step 2
     */
    @Query("""
            select w
            from WorkflowInstance w, Submission s, RstExercise e
            where w.submissionId = s.id
              and s.exerciseId = e.id
              and e.deletedAt is null
              and w.status = 'ACTIVE'
              and w.currentStep = 2
              and e.toolkitSnapshot.center = :center
              and e.toolkitSnapshot.domain = :domain
            """)
    List<WorkflowInstance> findActiveCdhByCenterAndDomain(
            @Param("center") String center, @Param("domain") String domain);
}
