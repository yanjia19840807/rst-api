package com.cmacgm.gbs.rst.api.workflow.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.workflow.domain.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
