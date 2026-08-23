package com.cmacgm.gbs.rst.api.timesheet.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Batch header for one Daily or Monthly Timesheet sync.
 */
@Entity
@Table(name = "timesheet_sync_run")
public class TimesheetSyncRun {

    @Id
    private UUID id;

    @Column(nullable = false, length = 16)
    private String kind;

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

    @Column(name = "source_drive_item_id", length = 200)
    private String sourceDriveItemId;

    @Column(name = "source_etag", length = 200)
    private String sourceEtag;

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
     * Starts a LOADING run.
     *
     * @param kind DAILY or MONTHLY
     * @param syncDate business date
     * @param attemptNo same-day retry
     * @param startedAt start time
     * @return new run
     */
    public static TimesheetSyncRun startLoading(
            String kind, LocalDate syncDate, short attemptNo, Instant startedAt) {
        TimesheetSyncRun run = new TimesheetSyncRun();
        run.id = UUID.randomUUID();
        run.kind = kind;
        run.syncDate = syncDate;
        run.attemptNo = attemptNo;
        run.status = "LOADING";
        run.startedAt = startedAt;
        return run;
    }

    /**
     * Records the SharePoint file identity used for this run.
     *
     * @param driveItemId Graph item id
     * @param etag Graph etag
     */
    public void setSource(String driveItemId, String etag) {
        this.sourceDriveItemId = driveItemId;
        this.sourceEtag = etag;
    }

    /**
     * Promotes this run to ACTIVE.
     *
     * @param rowCount source row count
     * @param dataHash content hash
     * @param completedAt activation time
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
     * Archives a previously ACTIVE run.
     *
     * @param completedAt archive time
     */
    public void markArchived(Instant completedAt) {
        this.status = "ARCHIVED";
        if (this.completedAt == null) {
            this.completedAt = completedAt;
        }
    }

    /**
     * Marks the run FAILED.
     *
     * @param errorCode stable code
     * @param errorMessage summary
     * @param completedAt failure time
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

    public String getKind() {
        return kind;
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

    public String getSourceDriveItemId() {
        return sourceDriveItemId;
    }

    public String getSourceEtag() {
        return sourceEtag;
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
