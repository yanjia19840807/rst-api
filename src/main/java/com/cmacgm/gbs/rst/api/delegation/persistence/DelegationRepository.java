package com.cmacgm.gbs.rst.api.delegation.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.delegation.domain.Delegation;
import com.cmacgm.gbs.rst.api.delegation.domain.DelegationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persisted identity delegations.
 */
public interface DelegationRepository extends JpaRepository<Delegation, UUID> {

    /**
     * Grants issued by A, newest first.
     *
     * @param delegatorCcgid A
     * @return rows
     */
    @Query("""
            select d from Delegation d
            where upper(d.delegatorCcgid) = upper(:ccgid)
            order by d.createdAt desc
            """)
    List<Delegation> findGrantedBy(@Param("ccgid") String delegatorCcgid);

    /**
     * Grants received by B, newest first.
     *
     * @param delegateCcgid B
     * @return rows
     */
    @Query("""
            select d from Delegation d
            where upper(d.delegateCcgid) = upper(:ccgid)
            order by d.createdAt desc
            """)
    List<Delegation> findReceivedBy(@Param("ccgid") String delegateCcgid);

    /**
     * Open A → B row if one exists.
     *
     * @param delegatorCcgid A
     * @param delegateCcgid B
     * @param open PENDING and ACTIVE
     * @return matching rows
     */
    @Query("""
            select d from Delegation d
            where upper(d.delegatorCcgid) = upper(:delegator)
              and upper(d.delegateCcgid) = upper(:delegate)
              and d.status in :open
            """)
    List<Delegation> findOpenPair(
            @Param("delegator") String delegatorCcgid,
            @Param("delegate") String delegateCcgid,
            @Param("open") List<DelegationStatus> open);
}
