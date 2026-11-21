package com.cmacgm.gbs.rst.api.sourcedata.persistence;

import java.util.List;

import com.cmacgm.gbs.rst.api.sourcedata.domain.GbsSiteCode;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SKPI office to site-code map.
 */
public interface GbsSiteCodeRepository extends JpaRepository<GbsSiteCode, String> {

    /**
     * @return every office mapping, ordered by office name
     */
    List<GbsSiteCode> findAllByOrderByGbsSiteAsc();
}
