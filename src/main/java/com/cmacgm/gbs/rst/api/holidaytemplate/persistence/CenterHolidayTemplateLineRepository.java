package com.cmacgm.gbs.rst.api.holidaytemplate.persistence;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.holidaytemplate.domain.CenterHolidayTemplateLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CenterHolidayTemplateLineRepository
        extends JpaRepository<CenterHolidayTemplateLine, UUID> {

    List<CenterHolidayTemplateLine> findByTemplateIdAndDeletedAtIsNullOrderByHolidayDateAscHolidayNameAsc(
            UUID templateId);
}
