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
            from Toolkit toolkit, TimesheetPerson person, TimesheetPosition seat,
                 TimesheetScope scope, TimesheetSyncRun daily, TimesheetSyncRun monthly
            where person.id.syncRunId = daily.id
              and seat.id.syncRunId = daily.id
              and seat.id.positionId = person.positionId
              and scope.id.syncRunId = monthly.id
              and daily.kind = 'DAILY'
              and daily.status = 'ACTIVE'
              and monthly.kind = 'MONTHLY'
              and monthly.status = 'ACTIVE'
              and upper(person.id.ccgid) = upper(:ccgid)
              and seat.roleType = 'AGENT'
              and seat.parentPositionId = toolkit.supervisorPositionId
              and scope.id.supervisorPositionId = seat.parentPositionId
              and scope.id.pl3Code = toolkit.primaryPl3Code
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
