package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.OrgPositionParent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Parent edges of Daily position nodes.
 */
public interface OrgPositionParentRepository
        extends JpaRepository<OrgPositionParent, OrgPositionParent.Id> {

    /**
     * ACTIVE Daily parent edges of a position, any role.
     *
     * @param positionId child position
     * @return edges
     */
    @Query("""
            select e
            from OrgPositionParent e, SourceSnapshot r
            where e.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and e.id.positionId = :positionId
            order by e.id.roleType, e.id.parentPositionId, e.id.parentRoleType
            """)
    List<OrgPositionParent> findActiveByPositionId(@Param("positionId") String positionId);

    /**
     * ACTIVE Daily children of one parent node.
     *
     * @param parentPositionId parent position
     * @param parentRoleType parent role
     * @return child edges
     */
    @Query("""
            select e
            from OrgPositionParent e, SourceSnapshot r
            where e.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and e.id.parentPositionId = :parentPositionId
              and e.id.parentRoleType = :parentRoleType
            order by e.id.positionId, e.id.roleType
            """)
    List<OrgPositionParent> findActiveChildren(
            @Param("parentPositionId") String parentPositionId,
            @Param("parentRoleType") String parentRoleType);

    /**
     * ACTIVE Daily parent edges for the given child positions.
     *
     * @param center exact run center; blank matches all
     * @param positionIds child positions
     * @return edges
     */
    @Query("""
            select e
            from OrgPositionParent e, SourceSnapshot r
            where e.id.syncRunId = r.id
              and r.kind = 'DAILY'
              and r.status = 'ACTIVE'
              and (:center = '' or r.center = :center)
              and e.id.positionId in :positionIds
            order by e.id.positionId, e.id.roleType, e.id.parentPositionId, e.id.parentRoleType
            """)
    List<OrgPositionParent> findActiveByPositionIdIn(
            @Param("center") String center, @Param("positionIds") Collection<String> positionIds);

    /**
     * Parent edges in the ACTIVE MyHR snapshot.
     *
     * @param positionIds child positions
     * @return edges
     */
    @Query("""
            select e
            from OrgPositionParent e, SourceSnapshot r
            where e.id.syncRunId = r.id
              and r.kind = 'MYHR'
              and r.status = 'ACTIVE'
              and e.id.positionId in :positionIds
            order by e.id.positionId, e.id.roleType, e.id.parentPositionId
            """)
    List<OrgPositionParent> findMyHrByPositionIdIn(@Param("positionIds") Collection<String> positionIds);

    /**
     * Parent edges of one position in the ACTIVE MyHR snapshot.
     *
     * @param positionId child position
     * @return edges
     */
    @Query("""
            select e
            from OrgPositionParent e, SourceSnapshot r
            where e.id.syncRunId = r.id
              and r.kind = 'MYHR'
              and r.status = 'ACTIVE'
              and e.id.positionId = :positionId
            order by e.id.roleType, e.id.parentPositionId
            """)
    List<OrgPositionParent> findMyHrByPositionId(@Param("positionId") String positionId);

    /**
     * Children of one parent node in the ACTIVE MyHR snapshot.
     *
     * @param parentPositionId parent position
     * @param parentRoleType parent role
     * @return child edges
     */
    @Query("""
            select e
            from OrgPositionParent e, SourceSnapshot r
            where e.id.syncRunId = r.id
              and r.kind = 'MYHR'
              and r.status = 'ACTIVE'
              and e.id.parentPositionId = :parentPositionId
              and e.id.parentRoleType = :parentRoleType
            order by e.id.positionId, e.id.roleType
            """)
    List<OrgPositionParent> findMyHrChildren(
            @Param("parentPositionId") String parentPositionId,
            @Param("parentRoleType") String parentRoleType);

    /**
     * Drops parent edges for other runs of this kind and Center.
     *
     * @param kind DAILY
     * @param center GBS center
     * @param keepRunId ACTIVE Daily run to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from OrgPositionParent e
            where e.id.syncRunId in (
                select r.id from SourceSnapshot r
                where r.kind = :kind and r.center = :center and r.id <> :keepRunId
            )
            """)
    int deleteStaleForCenter(
            @Param("kind") String kind, @Param("center") String center, @Param("keepRunId") UUID keepRunId);
}
