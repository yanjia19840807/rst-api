package com.cmacgm.gbs.rst.api.toolkit.persistence;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitTeamSetup;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for Toolkit Team Setup snapshots. */
public interface ToolkitTeamSetupRepository extends JpaRepository<ToolkitTeamSetup, UUID> {
}
