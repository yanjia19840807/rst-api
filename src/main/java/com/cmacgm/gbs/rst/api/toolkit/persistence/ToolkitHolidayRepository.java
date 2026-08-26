package com.cmacgm.gbs.rst.api.toolkit.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for Toolkit holiday snapshots. */
public interface ToolkitHolidayRepository extends JpaRepository<ToolkitHoliday, UUID> {

    List<ToolkitHoliday> findByToolkitIdOrderByHolidayDateAscHolidayNameAsc(UUID toolkitId);

    @Modifying
    @Query("delete from ToolkitHoliday h where h.toolkitId = :toolkitId")
    void deleteByToolkitId(@Param("toolkitId") UUID toolkitId);
}
