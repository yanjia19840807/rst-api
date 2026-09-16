package com.cmacgm.gbs.rst.api.domainhead.persistence;

import com.cmacgm.gbs.rst.api.domainhead.domain.CenterLth;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * One LTH mapping per Center.
 */
public interface CenterLthRepository extends JpaRepository<CenterLth, String> {
}
