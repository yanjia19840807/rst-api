package com.cmacgm.gbs.rst.api.geography.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.geography.domain.GeoRegion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeoRegionRepository extends JpaRepository<GeoRegion, UUID> {

    List<GeoRegion> findAllByOrderByNameAsc();

    Optional<GeoRegion> findByNameIgnoreCase(String name);
}
