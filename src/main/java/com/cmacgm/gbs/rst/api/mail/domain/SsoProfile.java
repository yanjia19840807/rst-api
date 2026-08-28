package com.cmacgm.gbs.rst.api.mail.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Last-seen mail-capable login. Used to find the LTH of a Center. Email lives on Timesheet.
 */
@Entity
@Table(name = "sso_profile")
public class SsoProfile {

    @Id
    @Column(length = 32)
    private String ccgid;

    @Column(length = 120)
    private String center;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    protected SsoProfile() {
    }

    /**
     * @param ccgid identity
     * @param center SSO / product center
     * @param role mail-capable role
     * @param seenAt last login
     * @return row
     */
    public static SsoProfile of(String ccgid, String center, String role, Instant seenAt) {
        SsoProfile row = new SsoProfile();
        row.replace(ccgid, center, role, seenAt);
        return row;
    }

    /**
     * @param ccgid identity
     * @param center SSO / product center
     * @param role mail-capable role
     * @param seenAt last login
     */
    public void replace(String ccgid, String center, String role, Instant seenAt) {
        this.ccgid = ccgid;
        this.center = center;
        this.role = role;
        this.seenAt = seenAt;
    }

    public String getCcgid() {
        return ccgid;
    }

    public String getCenter() {
        return center;
    }

    public String getRole() {
        return role;
    }

    public Instant getSeenAt() {
        return seenAt;
    }
}
