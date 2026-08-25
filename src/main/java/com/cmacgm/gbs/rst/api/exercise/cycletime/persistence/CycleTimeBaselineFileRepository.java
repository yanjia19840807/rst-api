package com.cmacgm.gbs.rst.api.exercise.cycletime.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.cycletime.domain.CycleTimeBaselineFile;
import com.cmacgm.gbs.rst.api.exercise.cycletime.domain.CycleTimeBaselineFile.Pk;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for cycle-time baseline evidence files. */
public interface CycleTimeBaselineFileRepository extends JpaRepository<CycleTimeBaselineFile, Pk> {

    /**
     * Lists evidence files for a baseline in display order.
     *
     * @param cycleTimeBaselineId baseline id
     * @return ordered link rows
     */
    List<CycleTimeBaselineFile> findByCycleTimeBaselineIdOrderByDisplayOrderAsc(
            UUID cycleTimeBaselineId);
}
