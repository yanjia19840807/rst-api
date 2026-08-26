package com.cmacgm.gbs.rst.api.toolkit.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitProductionSupportItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for Toolkit Production Support snapshots. */
public interface ToolkitProductionSupportItemRepository
        extends JpaRepository<ToolkitProductionSupportItem, UUID> {

    List<ToolkitProductionSupportItem> findByToolkitIdOrderByCategoryAscActivityAsc(UUID toolkitId);

    @Modifying
    @Query("delete from ToolkitProductionSupportItem i where i.toolkitId = :toolkitId")
    void deleteByToolkitId(@Param("toolkitId") UUID toolkitId);
}
