package com.cmacgm.gbs.rst.api.domainhead.persistence;

import java.util.List;
import java.util.Optional;

import com.cmacgm.gbs.rst.api.domainhead.domain.CenterDomainHead;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * LTH Center × Domain CDH mappings.
 */
public interface CenterDomainHeadRepository extends JpaRepository<CenterDomainHead, CenterDomainHead.Id> {

    /**
     * All mappings for a Center.
     *
     * @param center GBS center
     * @return mappings
     */
    List<CenterDomainHead> findByIdCenterOrderByIdDomainAsc(String center);

    /**
     * Mapping for one Center × Domain.
     *
     * @param center GBS center
     * @param domain GBS domain
     * @return mapping
     */
    Optional<CenterDomainHead> findByIdCenterAndIdDomain(String center, String domain);
}
