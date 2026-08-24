package com.cmacgm.gbs.rst.api.toolkit.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ToolkitRepository extends JpaRepository<Toolkit, UUID> {

    @EntityGraph(attributePaths = "subtasks")
    @Query("""
            select distinct toolkit
            from Toolkit toolkit
            join TimesheetAssignment assignment
              on assignment.id.supervisorPositionId = toolkit.supervisorPositionId
             and assignment.id.pl3Code = toolkit.primaryPl3Code
            join TimesheetSyncRun run
              on assignment.id.syncRunId = run.id
            where upper(assignment.id.empCcgid) = upper(:ccgid)
              and run.kind = 'MONTHLY'
              and run.status = 'ACTIVE'
              and toolkit.deletedAt is null
            order by toolkit.name
            """)
    List<Toolkit> findAvailableToAgent(@Param("ccgid") String ccgid);

    @EntityGraph(attributePaths = "subtasks")
    @Query("""
            select toolkit
            from Toolkit toolkit
            where toolkit.id = :id
              and toolkit.deletedAt is null
            """)
    Optional<Toolkit> findActiveById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "subtasks")
    List<Toolkit> findBySupervisorPositionIdAndDeletedAtIsNullOrderByName(
            String supervisorPositionId);

    boolean existsBySupervisorPositionIdAndCenterAndDomainAndPl1AndPl2AndPrimaryPl3CodeAndDeletedAtIsNull(
            String supervisorPositionId,
            String center,
            String domain,
            String pl1,
            String pl2,
            String primaryPl3Code);

    boolean existsBySupervisorPositionIdAndNameAndDeletedAtIsNull(
            String supervisorPositionId, String name);

    boolean existsBySupervisorPositionIdAndNameAndIdNotAndDeletedAtIsNull(
            String supervisorPositionId, String name, UUID id);
}
