package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.SupervisorProcess;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Supervisor-to-process links for a Monthly snapshot.
 */
public interface SupervisorProcessRepository extends JpaRepository<SupervisorProcess, SupervisorProcess.Id> {

    /**
     * Drops supervisor links for other runs of this kind and Center.
     *
     * @param kind MONTHLY
     * @param center GBS center
     * @param keepRunId snapshot to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from SupervisorProcess s
            where s.id.syncRunId in (
                select r.id from SourceSnapshot r
                where r.kind = :kind and r.center = :center and r.id <> :keepRunId
            )
            """)
    int deleteStaleForCenter(
            @Param("kind") String kind, @Param("center") String center, @Param("keepRunId") UUID keepRunId);
}
