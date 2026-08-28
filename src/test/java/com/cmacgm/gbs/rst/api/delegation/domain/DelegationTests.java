package com.cmacgm.gbs.rst.api.delegation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DelegationTests {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void startsActiveWhenFromIsNow() {
        Delegation row = Delegation.create(
                "A1", "A", "B1", "B", Set.of("SUPERVISOR"), "KL", T0, T2, T0);

        assertThat(row.getStatus()).isEqualTo(DelegationStatus.ACTIVE);
        assertThat(row.isUsable(T0)).isTrue();
    }

    @Test
    void startsPendingThenActivates() {
        Delegation row = Delegation.create(
                "A1", "A", "B1", "B", Set.of("SUPERVISOR"), "KL", T1, T2, T0);

        assertThat(row.getStatus()).isEqualTo(DelegationStatus.PENDING);
        assertThat(row.refresh(T1)).isTrue();
        assertThat(row.getStatus()).isEqualTo(DelegationStatus.ACTIVE);
        assertThat(row.isUsable(T1)).isTrue();
    }

    @Test
    void expiresAfterValidUntil() {
        Delegation row = Delegation.create(
                "A1", "A", "B1", "B", Set.of("SUPERVISOR"), "KL", T0, T1, T0);

        assertThat(row.refresh(T1)).isTrue();
        assertThat(row.getStatus()).isEqualTo(DelegationStatus.EXPIRED);
        assertThat(row.isUsable(T1)).isFalse();
    }

    @Test
    void revokeStopsUseImmediately() {
        Delegation row = Delegation.create(
                "A1", "A", "B1", "B", Set.of("SUPERVISOR"), "KL", T0, T2, T0);
        row.revoke(T1);

        assertThat(row.getStatus()).isEqualTo(DelegationStatus.REVOKED);
        assertThat(row.isUsable(T1)).isFalse();
    }
}
