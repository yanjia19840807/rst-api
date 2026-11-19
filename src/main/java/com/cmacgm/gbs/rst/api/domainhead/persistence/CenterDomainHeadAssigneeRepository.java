package com.cmacgm.gbs.rst.api.domainhead.persistence;

import java.util.List;

import com.cmacgm.gbs.rst.api.domainhead.domain.CenterDomainHeadAssignee;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Domain Head candidates for a Center × Domain.
 */
public interface CenterDomainHeadAssigneeRepository
        extends JpaRepository<CenterDomainHeadAssignee, CenterDomainHeadAssignee.Id> {

    /**
     * Candidates for one Center, in selection order.
     *
     * @param center GBS center
     * @return candidates
     */
    List<CenterDomainHeadAssignee> findByKeyCenterOrderBySortOrderAsc(String center);

    /**
     * Candidates for one Center × Domain, in selection order.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @return candidates
     */
    List<CenterDomainHeadAssignee> findByKeyCenterAndKeyDomainOrderBySortOrderAsc(String center, String domain);

    /**
     * Removes every candidate for one Center × Domain.
     *
     * @param center GBS center
     * @param domain GBS domain
     */
    void deleteByKeyCenterAndKeyDomain(String center, String domain);
}
