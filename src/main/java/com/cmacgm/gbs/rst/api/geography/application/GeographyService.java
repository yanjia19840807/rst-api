package com.cmacgm.gbs.rst.api.geography.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.geography.api.dto.GeoGroupView;
import com.cmacgm.gbs.rst.api.geography.api.dto.SaveGeoGroupRequest;
import com.cmacgm.gbs.rst.api.geography.domain.CustomerCountry;
import com.cmacgm.gbs.rst.api.geography.domain.GeoCluster;
import com.cmacgm.gbs.rst.api.geography.domain.GeoRegion;
import com.cmacgm.gbs.rst.api.geography.persistence.CustomerCountryRepository;
import com.cmacgm.gbs.rst.api.geography.persistence.GeoClusterRepository;
import com.cmacgm.gbs.rst.api.geography.persistence.GeoRegionRepository;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fixed Customer Country catalog, admin Regions, and per-user Clusters.
 * Timesheet sync does not write the catalog.
 */
@Service
public class GeographyService {

    private final CustomerCountryRepository countries;
    private final GeoRegionRepository regions;
    private final GeoClusterRepository clusters;
    private final Clock clock;

    public GeographyService(
            CustomerCountryRepository countries,
            GeoRegionRepository regions,
            GeoClusterRepository clusters,
            Clock clock) {
        this.countries = countries;
        this.regions = regions;
        this.clusters = clusters;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<String> listCountries() {
        return countries.findAllByOrderByNameAsc().stream().map(CustomerCountry::getName).toList();
    }

    @Transactional(readOnly = true)
    public List<GeoGroupView> listRegions() {
        return regions.findAllByOrderByNameAsc().stream().map(GeographyService::regionView).toList();
    }

    @Transactional(readOnly = true)
    public List<GeoGroupView> listClusters(RstPrincipal principal) {
        return clusters.findByOwnerCcgidOrderByNameAsc(owner(principal)).stream()
                .map(GeographyService::clusterView)
                .toList();
    }

    @Transactional
    public GeoGroupView createRegion(RstPrincipal principal, SaveGeoGroupRequest request) {
        return saveRegion(null, principal, request);
    }

    @Transactional
    public GeoGroupView updateRegion(UUID id, RstPrincipal principal, SaveGeoGroupRequest request) {
        return saveRegion(id, principal, request);
    }

    @Transactional
    public void deleteRegion(UUID id) {
        GeoRegion region = regions.findById(id)
                .orElseThrow(() -> notFound("region-not-found", "The region was not found."));
        regions.delete(region);
    }

    @Transactional
    public GeoGroupView createCluster(RstPrincipal principal, SaveGeoGroupRequest request) {
        return saveCluster(null, principal, request);
    }

    @Transactional
    public GeoGroupView updateCluster(UUID id, RstPrincipal principal, SaveGeoGroupRequest request) {
        return saveCluster(id, principal, request);
    }

    @Transactional
    public void deleteCluster(UUID id, RstPrincipal principal) {
        GeoCluster cluster = ownedCluster(id, owner(principal));
        clusters.delete(cluster);
    }

    /**
     * Countries allowed by the selected region and cluster names.
     *
     * @param ownerCcgid signed-in user; clusters are loaded only for this owner
     * @param regionNames selected region names; empty means no region constraint
     * @param clusterNames selected cluster names; empty means no cluster constraint
     * @return null when both selections are empty; otherwise the intersection (possibly empty)
     */
    @Transactional(readOnly = true)
    public Set<String> countryLimit(String ownerCcgid, List<String> regionNames, List<String> clusterNames) {
        return CountryLimit.intersect(
                countriesOfRegions(regionNames),
                countriesOfClusters(ownerCcgid, clusterNames),
                null);
    }

    private GeoGroupView saveRegion(UUID id, RstPrincipal principal, SaveGeoGroupRequest request) {
        String name = requireText(request.name(), "Region name is required.");
        Set<String> selected = canonicalCountries(request.countries());
        regions.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (id == null || !existing.getId().equals(id)) {
                throw unprocessable("name-already-used", "Region " + existing.getName() + " already exists.");
            }
        });
        for (GeoRegion other : regions.findAll()) {
            if (id != null && other.getId().equals(id)) {
                continue;
            }
            for (String country : selected) {
                if (other.getCountries().contains(country)) {
                    throw assigned(country, "region", other.getName());
                }
            }
        }
        Instant now = clock.instant();
        String actor = owner(principal);
        GeoRegion region = id == null
                ? GeoRegion.create(name, selected, actor, now)
                : regions.findById(id)
                        .orElseThrow(() -> notFound("region-not-found", "The region was not found."));
        if (id != null) {
            region.rename(name, selected, actor, now);
        } else {
            regions.save(region);
        }
        return regionView(region);
    }

    private GeoGroupView saveCluster(UUID id, RstPrincipal principal, SaveGeoGroupRequest request) {
        String ownerCcgid = owner(principal);
        String name = requireText(request.name(), "Cluster name is required.");
        Set<String> selected = canonicalCountries(request.countries());
        clusters.findByOwnerCcgidAndNameIgnoreCase(ownerCcgid, name).ifPresent(existing -> {
            if (id == null || !existing.getId().equals(id)) {
                throw unprocessable("name-already-used", "Cluster " + existing.getName() + " already exists.");
            }
        });
        for (GeoCluster other : clusters.findByOwnerCcgidOrderByNameAsc(ownerCcgid)) {
            if (id != null && other.getId().equals(id)) {
                continue;
            }
            for (String country : selected) {
                if (other.getCountries().contains(country)) {
                    throw assigned(country, "cluster", other.getName());
                }
            }
        }
        Instant now = clock.instant();
        GeoCluster cluster = id == null
                ? GeoCluster.create(ownerCcgid, name, selected, ownerCcgid, now)
                : ownedCluster(id, ownerCcgid);
        if (id != null) {
            cluster.rename(name, selected, ownerCcgid, now);
        } else {
            clusters.save(cluster);
        }
        return clusterView(cluster);
    }

    private Set<String> countriesOfRegions(List<String> names) {
        List<String> selected = clean(names);
        if (selected.isEmpty()) {
            return null;
        }
        Set<String> result = new LinkedHashSet<>();
        for (String name : selected) {
            regions.findByNameIgnoreCase(name).ifPresent(region -> result.addAll(region.getCountries()));
        }
        return result;
    }

    private Set<String> countriesOfClusters(String ownerCcgid, List<String> names) {
        List<String> selected = clean(names);
        if (selected.isEmpty()) {
            return null;
        }
        String owner = ownerCcgid == null ? "" : ownerCcgid.trim();
        Set<String> result = new LinkedHashSet<>();
        for (String name : selected) {
            clusters.findByOwnerCcgidAndNameIgnoreCase(owner, name)
                    .ifPresent(cluster -> result.addAll(cluster.getCountries()));
        }
        return result;
    }

    private Set<String> canonicalCountries(List<String> raw) {
        List<String> selected = clean(raw);
        if (selected.isEmpty()) {
            throw unprocessable("countries-required", "Select at least one customer country.");
        }
        Set<String> canonical = new LinkedHashSet<>();
        for (String name : selected) {
            CustomerCountry country = countries.findByNameIgnoreCase(name)
                    .orElseThrow(() -> unprocessable(
                            "unknown-country", name + " is not in the customer country list."));
            canonical.add(country.getName());
        }
        return canonical;
    }

    private GeoCluster ownedCluster(UUID id, String ownerCcgid) {
        GeoCluster cluster = clusters.findById(id)
                .orElseThrow(() -> notFound("cluster-not-found", "The cluster was not found."));
        if (!ownerCcgid.equals(cluster.getOwnerCcgid())) {
            throw notFound("cluster-not-found", "The cluster was not found.");
        }
        return cluster;
    }

    private static GeoGroupView regionView(GeoRegion region) {
        return new GeoGroupView(region.getId(), region.getName(), sorted(region.getCountries()));
    }

    private static GeoGroupView clusterView(GeoCluster cluster) {
        return new GeoGroupView(cluster.getId(), cluster.getName(), sorted(cluster.getCountries()));
    }

    private static List<String> sorted(Set<String> countries) {
        return countries.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private static List<String> clean(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String name = value.trim();
            if (!cleaned.contains(name)) {
                cleaned.add(name);
            }
        }
        return cleaned;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw unprocessable("name-required", message);
        }
        return value.trim();
    }

    private static String owner(RstPrincipal principal) {
        if (principal == null || principal.realCcgid() == null || principal.realCcgid().isBlank()) {
            throw unprocessable("actor-required", "A signed-in user is required.");
        }
        return principal.realCcgid().trim();
    }

    private static ApiException assigned(String country, String kind, String groupName) {
        return unprocessable(
                "country-already-assigned",
                country + " is already assigned to " + kind + " " + groupName + ".");
    }

    private static ApiException unprocessable(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }

    private static ApiException notFound(String code, String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, code, detail);
    }
}
