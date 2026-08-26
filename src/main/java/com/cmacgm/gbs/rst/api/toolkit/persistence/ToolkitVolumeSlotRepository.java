package com.cmacgm.gbs.rst.api.toolkit.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitVolumeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for canonical Toolkit slot volumes. */
public interface ToolkitVolumeSlotRepository extends JpaRepository<ToolkitVolumeSlot, UUID> {

    List<ToolkitVolumeSlot> findByToolkitIdOrderBySlotStartAtAsc(UUID toolkitId);

    Optional<ToolkitVolumeSlot> findByToolkitIdAndSlotStartAt(UUID toolkitId, Instant slotStartAt);
}
