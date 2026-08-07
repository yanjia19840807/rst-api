package com.cmacgm.gbs.rst.api.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String ccgid;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(UUID id, String ccgid, String displayName, String email, Instant now) {
        this.id = id;
        this.ccgid = ccgid;
        this.displayName = displayName;
        this.email = email;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getCcgid() {
        return ccgid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return active;
    }
}
