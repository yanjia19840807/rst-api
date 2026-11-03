package com.cmacgm.gbs.rst.api.geography.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.geography.domain.GeoCluster;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeoClusterRepository extends JpaRepository<GeoCluster, UUID> {

    List<GeoCluster> findByOwnerCcgidOrderByNameAsc(String ownerCcgid);

    Optional<GeoCluster> findByOwnerCcgidAndNameIgnoreCase(String ownerCcgid, String name);
}
