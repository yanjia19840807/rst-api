package com.cmacgm.gbs.rst.api.tms.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.cmacgm.gbs.rst.api.identity.domain.AppUser;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSubtask;

@Entity
@Table(name = "tms_session")
public class TmsSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_no", nullable = false, unique = true, length = 80)
    private String sessionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "toolkit_id", nullable = false)
    private Toolkit toolkit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "toolkit_subtask_id")
    private ToolkitSubtask toolkitSubtask;

    @Column(name = "processed_volume")
    private Integer processedVolume;

    @Column(nullable = false, length = 100)
    private String reference;

    @Column(nullable = false, length = 500)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TmsSessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "running_since")
    private Instant runningSince;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "net_duration_seconds", nullable = false)
    private long netDurationSeconds;

    @Column(name = "gross_duration_seconds", nullable = false)
    private long grossDurationSeconds;

    @Column(name = "pause_duration_seconds", nullable = false)
    private long pauseDurationSeconds;

    @Column(name = "toolkit_name_snapshot", nullable = false, length = 200)
    private String toolkitNameSnapshot;

    @Column(name = "subtask_name_snapshot", nullable = false, length = 200)
    private String subtaskNameSnapshot;

    @Column(name = "domain_snapshot", nullable = false, length = 120)
    private String domainSnapshot;

    @Column(name = "pl1_snapshot", nullable = false, length = 200)
    private String pl1Snapshot;

    @Column(name = "pl2_snapshot", nullable = false, length = 200)
    private String pl2Snapshot;

    @Column(name = "pl3_code_snapshot", nullable = false, length = 80)
    private String pl3CodeSnapshot;

    @Column(name = "pl3_name_snapshot", nullable = false, length = 200)
    private String pl3NameSnapshot;

    @Column(name = "discard_reason")
    private String discardReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pausedAt ASC")
    private List<TmsPauseInterval> pauseIntervals = new ArrayList<>();

    protected TmsSession() {
    }

    public static TmsSession start(
            String sessionNo,
            AppUser user,
            Toolkit toolkit,
            ToolkitSubtask subtask,
            Integer processedVolume,
            String reference,
            String remarks,
            Instant now) {
        TmsSession session = new TmsSession();
        session.sessionNo = sessionNo;
        session.user = user;
        session.toolkit = toolkit;
        session.toolkitSubtask = subtask;
        session.processedVolume = processedVolume;
        session.reference = reference;
        session.remarks = remarks;
        session.status = TmsSessionStatus.RUNNING;
        session.startedAt = now;
        session.runningSince = now;
        session.netDurationSeconds = 0;
        session.toolkitNameSnapshot = toolkit.getName();
        session.subtaskNameSnapshot = subtask == null ? "—" : subtask.getName();
        session.domainSnapshot = toolkit.getDomain();
        session.pl1Snapshot = toolkit.getPl1();
        session.pl2Snapshot = toolkit.getPl2();
        session.pl3CodeSnapshot = toolkit.getPrimaryPl3Code();
        session.pl3NameSnapshot = toolkit.getPl3Name();
        session.createdAt = now;
        session.updatedAt = now;
        return session;
    }

    public void pause(Instant now) {
        requireStatus(TmsSessionStatus.RUNNING, "Only a running session can be paused.");
        netDurationSeconds += secondsSinceRunning(now);
        runningSince = null;
        pausedAt = now;
        status = TmsSessionStatus.PAUSED;
        pauseIntervals.add(new TmsPauseInterval(this, now));
        updatedAt = now;
    }

    public void resume(Instant now) {
        requireStatus(TmsSessionStatus.PAUSED, "Only a paused session can be resumed.");
        TmsPauseInterval openInterval = pauseIntervals.stream()
                .filter(interval -> interval.getResumedAt() == null)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new TmsStateException("The paused session has no open pause interval."));
        openInterval.resume(now);
        status = TmsSessionStatus.RUNNING;
        runningSince = now;
        pausedAt = null;
        updatedAt = now;
    }

    public void end(Instant now) {
        requireStatus(TmsSessionStatus.RUNNING, "Only a running session can be ended.");
        netDurationSeconds += secondsSinceRunning(now);
        runningSince = null;
        endedAt = now;
        status = TmsSessionStatus.COMPLETED;
        recalculateFinalDurations(now);
        updatedAt = now;
    }

    public void discard(String reason, Instant now) {
        if (status == TmsSessionStatus.DISCARDED) {
            throw new TmsStateException("The session is already discarded.");
        }
        if (status == TmsSessionStatus.RUNNING) {
            netDurationSeconds += secondsSinceRunning(now);
            runningSince = null;
        } else if (status == TmsSessionStatus.PAUSED) {
            pauseIntervals.stream()
                    .filter(interval -> interval.getResumedAt() == null)
                    .findFirst()
                    .ifPresent(interval -> interval.resume(now));
            pausedAt = null;
        }
        endedAt = endedAt == null ? now : endedAt;
        status = TmsSessionStatus.DISCARDED;
        discardReason = reason;
        recalculateFinalDurations(endedAt);
        updatedAt = now;
    }

    public long elapsedSeconds(Instant now) {
        return status == TmsSessionStatus.RUNNING
                ? netDurationSeconds + secondsSinceRunning(now)
                : netDurationSeconds;
    }

    private void recalculateFinalDurations(Instant end) {
        grossDurationSeconds = Duration.between(startedAt, end).toSeconds();
        pauseDurationSeconds = pauseIntervals.stream()
                .mapToLong(interval -> interval.durationSeconds(end))
                .sum();
    }

    private long secondsSinceRunning(Instant now) {
        if (runningSince == null || now.isBefore(runningSince)) {
            throw new TmsStateException("The session running timestamp is invalid.");
        }
        return Duration.between(runningSince, now).toSeconds();
    }

    private void requireStatus(TmsSessionStatus required, String message) {
        if (status != required) {
            throw new TmsStateException(message);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getSessionNo() {
        return sessionNo;
    }

    public AppUser getUser() {
        return user;
    }

    public Toolkit getToolkit() {
        return toolkit;
    }

    public ToolkitSubtask getToolkitSubtask() {
        return toolkitSubtask;
    }

    public Integer getProcessedVolume() {
        return processedVolume;
    }

    public String getReference() {
        return reference;
    }

    public String getRemarks() {
        return remarks;
    }

    public TmsSessionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getRunningSince() {
        return runningSince;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public long getNetDurationSeconds() {
        return netDurationSeconds;
    }

    public long getGrossDurationSeconds() {
        return grossDurationSeconds;
    }

    public long getPauseDurationSeconds() {
        return pauseDurationSeconds;
    }

    public String getToolkitNameSnapshot() {
        return toolkitNameSnapshot;
    }

    public String getSubtaskNameSnapshot() {
        return subtaskNameSnapshot;
    }

    public String getDomainSnapshot() {
        return domainSnapshot;
    }

    public String getPl1Snapshot() {
        return pl1Snapshot;
    }

    public String getPl2Snapshot() {
        return pl2Snapshot;
    }

    public String getPl3CodeSnapshot() {
        return pl3CodeSnapshot;
    }

    public String getPl3NameSnapshot() {
        return pl3NameSnapshot;
    }

    public String getDiscardReason() {
        return discardReason;
    }

    public long getVersion() {
        return version;
    }
}
