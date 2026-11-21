package com.cmacgm.gbs.rst.api.sourcedata.persistence;

import java.util.List;

import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterDeliveryHc;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Center Delivery HC lines.
 */
public interface CenterDeliveryHcRepository extends JpaRepository<CenterDeliveryHc, CenterDeliveryHc.Id> {

    /**
     * Delivery HC of one center in the ACTIVE SKPI snapshot.
     *
     * @param center GBS center
     * @param q process, carrier, site, or country
     * @param pageable page
     * @return HC lines
     */
    @Query(
            value = """
                    select h
                    from CenterDeliveryHc h, SourceSnapshot r
                    where h.id.syncRunId = r.id
                      and r.kind = 'SHARED_KPI'
                      and r.status = 'ACTIVE'
                      and h.id.center = :center
                      and (:q = ''
                           or lower(h.id.pl3Code) like lower(concat('%', :q, '%'))
                           or lower(h.id.carrier) like lower(concat('%', :q, '%'))
                           or lower(h.id.site) like lower(concat('%', :q, '%'))
                           or lower(h.id.customerCountry) like lower(concat('%', :q, '%')))
                    order by h.id.pl3Code, h.id.carrier, h.id.site, h.id.customerCountry
                    """,
            countQuery = """
                    select count(h)
                    from CenterDeliveryHc h, SourceSnapshot r
                    where h.id.syncRunId = r.id
                      and r.kind = 'SHARED_KPI'
                      and r.status = 'ACTIVE'
                      and h.id.center = :center
                      and (:q = ''
                           or lower(h.id.pl3Code) like lower(concat('%', :q, '%'))
                           or lower(h.id.carrier) like lower(concat('%', :q, '%'))
                           or lower(h.id.site) like lower(concat('%', :q, '%'))
                           or lower(h.id.customerCountry) like lower(concat('%', :q, '%')))
                    """)
    Page<CenterDeliveryHc> searchActive(
            @Param("center") String center, @Param("q") String q, Pageable pageable);

    /**
     * Delivery HC lines for one center and process in the ACTIVE SKPI snapshot.
     *
     * @param center GBS center
     * @param pl3Code process level 3
     * @return HC lines
     */
    @Query("""
            select h
            from CenterDeliveryHc h, SourceSnapshot r
            where h.id.syncRunId = r.id
              and r.kind = 'SHARED_KPI'
              and r.status = 'ACTIVE'
              and h.id.center = :center
              and h.id.pl3Code = :pl3Code
            order by h.id.carrier, h.id.site, h.id.customerCountry
            """)
    List<CenterDeliveryHc> findActiveByCenterAndPl3(
            @Param("center") String center, @Param("pl3Code") String pl3Code);

    /**
     * Customer countries for one center and process in the ACTIVE SKPI snapshot.
     *
     * @param center GBS center
     * @param pl3Code process level 3
     * @return countries
     */
    @Query("""
            select distinct h.id.customerCountry
            from CenterDeliveryHc h, SourceSnapshot r
            where h.id.syncRunId = r.id
              and r.kind = 'SHARED_KPI'
              and r.status = 'ACTIVE'
              and h.id.center = :center
              and h.id.pl3Code = :pl3Code
            order by h.id.customerCountry
            """)
    List<String> findActiveCountries(@Param("center") String center, @Param("pl3Code") String pl3Code);
}
