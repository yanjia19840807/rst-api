package com.cmacgm.gbs.rst.api.toolkit.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitVolumeMonthly;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for canonical Toolkit monthly volumes. */
public interface ToolkitVolumeMonthlyRepository extends JpaRepository<ToolkitVolumeMonthly, UUID> {

    List<ToolkitVolumeMonthly> findByToolkitIdOrderByMonthAsc(UUID toolkitId);

    Optional<ToolkitVolumeMonthly> findByToolkitIdAndMonth(UUID toolkitId, LocalDate month);
}
