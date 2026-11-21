package com.cmacgm.gbs.rst.api.timesheet.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.timesheet.domain.ProcessScope;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Process rows for a snapshot.
 */
public interface ProcessScopeRepository extends JpaRepository<ProcessScope, ProcessScope.Id> {

    /**
     * Processes of one center in the ACTIVE SKPI snapshot.
     *
     * @param center GBS center
     * @param q domain, PL3 code, or PL3 name
     * @param pageable page
     * @return process rows
     */
    @Query(
            value = """
                    select p
                    from ProcessScope p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'SHARED_KPI'
                      and r.status = 'ACTIVE'
                      and p.id.center = :center
                      and (:q = ''
                           or lower(p.domain) like lower(concat('%', :q, '%'))
                           or lower(p.id.pl3Code) like lower(concat('%', :q, '%'))
                           or lower(p.pl3Name) like lower(concat('%', :q, '%'))
                           or lower(p.pl1) like lower(concat('%', :q, '%'))
                           or lower(p.pl2) like lower(concat('%', :q, '%')))
                    order by p.domain, p.id.pl3Code
                    """,
            countQuery = """
                    select count(p)
                    from ProcessScope p, SourceSnapshot r
                    where p.id.syncRunId = r.id
                      and r.kind = 'SHARED_KPI'
                      and r.status = 'ACTIVE'
                      and p.id.center = :center
                      and (:q = ''
                           or lower(p.domain) like lower(concat('%', :q, '%'))
                           or lower(p.id.pl3Code) like lower(concat('%', :q, '%'))
                           or lower(p.pl3Name) like lower(concat('%', :q, '%'))
                           or lower(p.pl1) like lower(concat('%', :q, '%'))
                           or lower(p.pl2) like lower(concat('%', :q, '%')))
                    """)
    Page<ProcessScope> searchActiveShared(
            @Param("center") String center, @Param("q") String q, Pageable pageable);

    /**
     * Distinct domains in the ACTIVE SKPI snapshot for one center.
     *
     * @param center GBS center
     * @return domain names
     */
    @Query("""
            select distinct p.domain
            from ProcessScope p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'SHARED_KPI'
              and r.status = 'ACTIVE'
              and p.id.center = :center
              and p.domain <> ''
            order by p.domain
            """)
    List<String> findActiveSharedDomainsByCenter(@Param("center") String center);

    /**
     * Processes of one center in the ACTIVE SKPI snapshot.
     *
     * @param center GBS center
     * @return process rows
     */
    @Query("""
            select p
            from ProcessScope p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'SHARED_KPI'
              and r.status = 'ACTIVE'
              and p.id.center = :center
            order by p.domain, p.pl1, p.pl2, p.id.pl3Code
            """)
    List<ProcessScope> findActiveSharedByCenter(@Param("center") String center);

    /**
     * Whether this center still has the process in the ACTIVE SKPI snapshot.
     *
     * @param center GBS center
     * @param pl3Code process level 3
     * @return true when the process row exists
     */
    @Query("""
            select count(p) > 0
            from ProcessScope p, SourceSnapshot r
            where p.id.syncRunId = r.id
              and r.kind = 'SHARED_KPI'
              and r.status = 'ACTIVE'
              and p.id.center = :center
              and p.id.pl3Code = :pl3Code
            """)
    boolean existsActiveShared(@Param("center") String center, @Param("pl3Code") String pl3Code);

    /**
     * Drops process rows for other runs of this kind and Center.
     *
     * @param kind MONTHLY
     * @param center GBS center
     * @param keepRunId snapshot to keep
     * @return deleted rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ProcessScope p
            where p.id.syncRunId in (
                select r.id from SourceSnapshot r
                where r.kind = :kind and r.center = :center and r.id <> :keepRunId
            )
            """)
    int deleteStaleForCenter(
            @Param("kind") String kind, @Param("center") String center, @Param("keepRunId") UUID keepRunId);
}
