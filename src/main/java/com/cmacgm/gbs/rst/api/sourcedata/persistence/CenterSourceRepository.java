package com.cmacgm.gbs.rst.api.sourcedata.persistence;

import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterSource;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Center source mode.
 */
public interface CenterSourceRepository extends JpaRepository<CenterSource, String> {
}
