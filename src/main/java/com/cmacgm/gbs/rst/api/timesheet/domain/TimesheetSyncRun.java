package com.cmacgm.gbs.rst.api.timesheet.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Batch header for one full Timesheet snapshot load.
 */
@Entity
@Table(name = "timesheet_sync_run")
public class TimesheetSyncRun {

    @Id
    private UUID id;

    @Column(name = "sync_date", nullable = false)
    private LocalDate syncDate;

    @Column(name = "attempt_no", nullable = false)
    private short attemptNo;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "data_hash", length = 64)
    private String dataHash;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    protected TimesheetSyncRun() {
    }

    /**
     * Starts a new LOADING run for the given business sync date.
     *
     * @param syncDate business synchronization date
     * @param attemptNo same-day retry sequence (1-based)
     * @param startedAt load start timestamp
     * @return new LOADING run
     */
    public static TimesheetSyncRun startLoading(LocalDate syncDate, short attemptNo, Instant startedAt) {
        TimesheetSyncRun run = new TimesheetSyncRun();
        run.id = UUID.randomUUID();
        run.syncDate = syncDate;
        run.attemptNo = attemptNo;
        run.status = "LOADING";
        run.startedAt = startedAt;
        return run;
    }

    /**
     * Promotes this run to the sole ACTIVE snapshot.
     *
     * @param rowCount accepted row count
     * @param dataHash SHA-256 of the normalized payload
     * @param completedAt activation timestamp
     */
    public void markActive(int rowCount, String dataHash, Instant completedAt) {
        this.status = "ACTIVE";
        this.rowCount = rowCount;
        this.dataHash = dataHash;
        this.completedAt = completedAt;
        this.errorCode = null;
        this.errorMessage = null;
    }

    /**
     * Archives a previously ACTIVE run after a successful cutover.
     *
     * @param completedAt archive timestamp
     */
    public void markArchived(Instant completedAt) {
        this.status = "ARCHIVED";
        if (this.completedAt == null) {
            this.completedAt = completedAt;
        }
    }

    /**
     * Marks the run FAILED while keeping any previously ACTIVE snapshot unchanged.
     *
     * @param errorCode stable failure category
     * @param errorMessage sanitized summary
     * @param completedAt failure timestamp
     */
    public void markFailed(String errorCode, String errorMessage, Instant completedAt) {
        this.status = "FAILED";
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.completedAt = completedAt;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getSyncDate() {
        return syncDate;
    }

    public short getAttemptNo() {
        return attemptNo;
    }

    public String getStatus() {
        return status;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public String getDataHash() {
        return dataHash;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
