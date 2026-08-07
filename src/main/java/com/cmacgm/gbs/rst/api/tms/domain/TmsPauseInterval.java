package com.cmacgm.gbs.rst.api.tms.domain;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tms_pause_interval")
public class TmsPauseInterval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tms_session_id", nullable = false)
    private TmsSession session;

    @Column(name = "paused_at", nullable = false)
    private Instant pausedAt;

    @Column(name = "resumed_at")
    private Instant resumedAt;

    protected TmsPauseInterval() {
    }

    TmsPauseInterval(TmsSession session, Instant pausedAt) {
        this.session = session;
        this.pausedAt = pausedAt;
    }

    void resume(Instant resumedAt) {
        if (this.resumedAt != null) {
            throw new TmsStateException("The pause interval is already closed.");
        }
        if (resumedAt.isBefore(pausedAt)) {
            throw new TmsStateException("Resume time cannot be before pause time.");
        }
        this.resumedAt = resumedAt;
    }

    long durationSeconds(Instant now) {
        return Duration.between(pausedAt, resumedAt == null ? now : resumedAt).toSeconds();
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public Instant getResumedAt() {
        return resumedAt;
    }
}
