package com.cmacgm.gbs.rst.api.toolkit.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitVolumeDaily;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for canonical Toolkit daily volumes. */
public interface ToolkitVolumeDailyRepository extends JpaRepository<ToolkitVolumeDaily, UUID> {

    List<ToolkitVolumeDaily> findByToolkitIdOrderByVolumeDateAsc(UUID toolkitId);

    Optional<ToolkitVolumeDaily> findByToolkitIdAndVolumeDate(UUID toolkitId, LocalDate volumeDate);
}
