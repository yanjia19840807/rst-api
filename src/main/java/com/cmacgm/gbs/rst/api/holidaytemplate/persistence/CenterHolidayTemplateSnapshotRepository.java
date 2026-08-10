package com.cmacgm.gbs.rst.api.holidaytemplate.persistence;

import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.holidaytemplate.domain.CenterHolidayTemplateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CenterHolidayTemplateSnapshotRepository
        extends JpaRepository<CenterHolidayTemplateSnapshot, UUID> {

    Optional<CenterHolidayTemplateSnapshot> findByTemplateIdAndVersion(UUID templateId, int version);

    /**
     * Latest published snapshot for a Center + year (survives draft reopen of the header).
     */
    Optional<CenterHolidayTemplateSnapshot> findFirstByCenterIgnoreCaseAndYearOrderByVersionDesc(
            String center, short year);
}
