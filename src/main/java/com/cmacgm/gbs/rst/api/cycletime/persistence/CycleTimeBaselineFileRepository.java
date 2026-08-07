package com.cmacgm.gbs.rst.api.cycletime.persistence;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaselineFile;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaselineFile.Pk;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for cycle-time baseline evidence files. */
public interface CycleTimeBaselineFileRepository extends JpaRepository<CycleTimeBaselineFile, Pk> {
}
