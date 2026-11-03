package com.cmacgm.gbs.rst.api.geography.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** Admin-defined region. A customer country belongs to at most one region. */
@Entity
@Table(name = "geo_region")
public class GeoRegion {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "geo_region_country", joinColumns = @JoinColumn(name = "region_id"))
    @Column(name = "country_name", nullable = false, length = 120)
    private Set<String> countries = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected GeoRegion() {
    }

    public static GeoRegion create(String name, Set<String> countries, String actor, Instant now) {
        GeoRegion region = new GeoRegion();
        region.id = UUID.randomUUID();
        region.name = name;
        region.countries.addAll(countries);
        region.createdAt = now;
        region.createdBy = actor;
        region.updatedAt = now;
        region.updatedBy = actor;
        return region;
    }

    public void rename(String name, Set<String> countries, String actor, Instant now) {
        this.name = name;
        this.countries.clear();
        this.countries.addAll(countries);
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<String> getCountries() {
        return countries;
    }
}
