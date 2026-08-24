package com.cmacgm.gbs.rst.api.workflow.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.workflow.domain.ProcessInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for process instances. */
public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, UUID> {

    /**
     * Finds the process for an Exercise (at most one).
     *
     * @param exerciseId Exercise id
     * @return optional instance
     */
    Optional<ProcessInstance> findByExerciseId(UUID exerciseId);

    /**
     * Finds processes for the given Exercises.
     *
     * @param exerciseIds Exercise ids
     * @return instances
     */
    List<ProcessInstance> findByExerciseIdIn(Collection<UUID> exerciseIds);

    /**
     * Lists processes in the given statuses, newest first.
     *
     * @param statuses status filter
     * @return instances
     */
    List<ProcessInstance> findByStatusInOrderBySubmittedAtDesc(Collection<ProcessStatus> statuses);

    /**
     * OPEN processes currently waiting on CDH for Toolkits in this Center × Domain.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @return instances at the CDH node
     */
    @Query("""
            select distinct w
            from ProcessInstance w, RstExercise e
            join w.tasks t
            where w.exerciseId = e.id
              and e.deletedAt is null
              and w.status = com.cmacgm.gbs.rst.api.workflow.domain.ProcessStatus.OPEN
              and t.status = com.cmacgm.gbs.rst.api.workflow.domain.TaskStatus.PENDING
              and t.node = com.cmacgm.gbs.rst.api.workflow.domain.TaskNode.CDH
              and e.toolkitSnapshot.center = :center
              and e.toolkitSnapshot.domain = :domain
            """)
    List<ProcessInstance> findOpenCdhByCenterAndDomain(
            @Param("center") String center, @Param("domain") String domain);
}
