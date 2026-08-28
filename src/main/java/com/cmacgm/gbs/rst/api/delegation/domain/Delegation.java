package com.cmacgm.gbs.rst.api.delegation.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One authorization for B to act as A.
 */
@Entity
@Table(name = "rst_delegation")
public class Delegation {

    @Id
    private UUID id;

    @Column(name = "delegator_ccgid", nullable = false, length = 64)
    private String delegatorCcgid;

    @Column(name = "delegator_name", length = 200)
    private String delegatorName;

    @Column(name = "delegate_ccgid", nullable = false, length = 64)
    private String delegateCcgid;

    @Column(name = "delegate_name", length = 200)
    private String delegateName;

    @Column(name = "delegator_roles", nullable = false, length = 200)
    private String delegatorRoles;

    @Column(name = "delegator_center", length = 120)
    private String delegatorCenter;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DelegationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Delegation() {
    }

    /**
     * Creates an open delegation.
     *
     * @param delegatorCcgid A
     * @param delegatorName A's name
     * @param delegateCcgid B
     * @param delegateName B's name
     * @param delegatorRoles snapshot of A's roles
     * @param delegatorCenter snapshot of A's center
     * @param validFrom start
     * @param validUntil end
     * @param now create time
     * @return row
     */
    public static Delegation create(
            String delegatorCcgid,
            String delegatorName,
            String delegateCcgid,
            String delegateName,
            Set<String> delegatorRoles,
            String delegatorCenter,
            Instant validFrom,
            Instant validUntil,
            Instant now) {
        Delegation row = new Delegation();
        row.id = UUID.randomUUID();
        row.delegatorCcgid = delegatorCcgid.trim().toUpperCase(Locale.ROOT);
        row.delegatorName = delegatorName;
        row.delegateCcgid = delegateCcgid.trim().toUpperCase(Locale.ROOT);
        row.delegateName = delegateName;
        row.delegatorRoles = joinRoles(delegatorRoles);
        row.delegatorCenter = delegatorCenter;
        row.validFrom = validFrom;
        row.validUntil = validUntil;
        row.createdAt = now;
        row.status = !now.isBefore(validFrom) ? DelegationStatus.ACTIVE : DelegationStatus.PENDING;
        return row;
    }

    /**
     * Moves PENDING → ACTIVE and open rows past {@code validUntil} → EXPIRED.
     *
     * @param now clock
     * @return true when status changed
     */
    public boolean refresh(Instant now) {
        if (status == DelegationStatus.REVOKED) {
            return false;
        }
        if (!now.isBefore(validUntil) && status != DelegationStatus.EXPIRED) {
            status = DelegationStatus.EXPIRED;
            return true;
        }
        if (status == DelegationStatus.PENDING && !now.isBefore(validFrom) && now.isBefore(validUntil)) {
            status = DelegationStatus.ACTIVE;
            return true;
        }
        return false;
    }

    /**
     * Revokes an open delegation.
     *
     * @param now revoke time
     */
    public void revoke(Instant now) {
        this.status = DelegationStatus.REVOKED;
        this.revokedAt = now;
    }

    /**
     * @param now clock
     * @return true when B may use this row right now
     */
    public boolean isUsable(Instant now) {
        return status == DelegationStatus.ACTIVE
                && !now.isBefore(validFrom)
                && now.isBefore(validUntil);
    }

    /**
     * @return snapshotted roles
     */
    public Set<String> roleSet() {
        if (delegatorRoles == null || delegatorRoles.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(delegatorRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * @return ended-at for history (revoke or planned end)
     */
    public Instant endedAt() {
        if (status == DelegationStatus.REVOKED) {
            return revokedAt;
        }
        if (status == DelegationStatus.EXPIRED) {
            return validUntil;
        }
        return null;
    }

    private static String joinRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        return roles.stream()
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    public UUID getId() {
        return id;
    }

    public String getDelegatorCcgid() {
        return delegatorCcgid;
    }

    public String getDelegatorName() {
        return delegatorName;
    }

    public String getDelegateCcgid() {
        return delegateCcgid;
    }

    public String getDelegateName() {
        return delegateName;
    }

    public String getDelegatorRoles() {
        return delegatorRoles;
    }

    public String getDelegatorCenter() {
        return delegatorCenter;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public DelegationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
