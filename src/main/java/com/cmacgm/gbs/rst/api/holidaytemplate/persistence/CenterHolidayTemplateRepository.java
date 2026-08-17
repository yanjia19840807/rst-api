package com.cmacgm.gbs.rst.api.holidaytemplate.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.holidaytemplate.domain.CenterHolidayTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CenterHolidayTemplateRepository extends JpaRepository<CenterHolidayTemplate, UUID> {

    /**
     * Optional filters. Cast bind params so PostgreSQL does not treat null as bytea
     * (which breaks {@code lower(:center)}).
     */
    @Query("""
            select t from CenterHolidayTemplate t
            where t.deletedAt is null
              and (cast(:center as string) is null
                   or lower(t.center) = lower(cast(:center as string)))
              and (cast(:year as short) is null or t.year = cast(:year as short))
              and (cast(:status as string) is null or t.status = cast(:status as string))
            order by t.center asc, t.year desc
            """)
    List<CenterHolidayTemplate> search(
            @Param("center") String center,
            @Param("year") Short year,
            @Param("status") String status);

    Optional<CenterHolidayTemplate> findByIdAndDeletedAtIsNull(UUID id);

    Optional<CenterHolidayTemplate> findByCenterIgnoreCaseAndYearAndDeletedAtIsNull(
            String center, short year);
}
