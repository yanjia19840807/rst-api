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

/** Personal cluster. A customer country belongs to at most one cluster of the same user. */
@Entity
@Table(name = "geo_cluster")
public class GeoCluster {

    @Id
    private UUID id;

    @Column(name = "owner_ccgid", nullable = false, length = 64)
    private String ownerCcgid;

    @Column(nullable = false, length = 120)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "geo_cluster_country", joinColumns = @JoinColumn(name = "cluster_id"))
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

    protected GeoCluster() {
    }

    public static GeoCluster create(
            String ownerCcgid, String name, Set<String> countries, String actor, Instant now) {
        GeoCluster cluster = new GeoCluster();
        cluster.id = UUID.randomUUID();
        cluster.ownerCcgid = ownerCcgid;
        cluster.name = name;
        cluster.countries.addAll(countries);
        cluster.createdAt = now;
        cluster.createdBy = actor;
        cluster.updatedAt = now;
        cluster.updatedBy = actor;
        return cluster;
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

    public String getOwnerCcgid() {
        return ownerCcgid;
    }

    public String getName() {
        return name;
    }

    public Set<String> getCountries() {
        return countries;
    }
}
