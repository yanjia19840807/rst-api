package com.cmacgm.gbs.rst.api.timesheet.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One ERROR from a Timesheet sync. {@code MISSING_FIELD} is advisory.
 */
@Entity
@Table(name = "timesheet_sync_issue")
public class TimesheetSyncIssue {

    @Id
    private UUID id;

    @Column(name = "sync_run_id", nullable = false)
    private UUID syncRunId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false)
    private String message;

    @Column(name = "emp_id", length = 80)
    private String empId;

    @Column(name = "emp_ccgid", length = 32)
    private String empCcgid;

    @Column(name = "position_id", length = 80)
    private String positionId;

    @Column(name = "pl3_code", length = 80)
    private String pl3Code;

    @Column(name = "source_row")
    private Integer sourceRow;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TimesheetSyncIssue() {
    }

    /**
     * Creates an ERROR issue from a catalog code.
     *
     * @param syncRunId owning run
     * @param code catalog code
     * @param message human message
     * @param empId optional person id
     * @param empCcgid optional identity
     * @param positionId optional position
     * @param pl3Code optional PL3
     * @param sourceRow optional Excel row
     * @param createdAt created at
     * @return issue
     */
    public static TimesheetSyncIssue error(
            UUID syncRunId,
            TimesheetSyncErrorCode code,
            String message,
            String empId,
            String empCcgid,
            String positionId,
            String pl3Code,
            Integer sourceRow,
            Instant createdAt) {
        return error(
                syncRunId,
                code.code(),
                message,
                empId,
                empCcgid,
                positionId,
                pl3Code,
                sourceRow,
                createdAt);
    }

    /**
     * Creates an ERROR issue from a persisted or external code.
     *
     * @param syncRunId owning run
     * @param code stable code
     * @param message human message
     * @param empId optional person id
     * @param empCcgid optional identity
     * @param positionId optional position
     * @param pl3Code optional PL3
     * @param sourceRow optional Excel row
     * @param createdAt created at
     * @return issue
     */
    public static TimesheetSyncIssue error(
            UUID syncRunId,
            String code,
            String message,
            String empId,
            String empCcgid,
            String positionId,
            String pl3Code,
            Integer sourceRow,
            Instant createdAt) {
        TimesheetSyncIssue issue = new TimesheetSyncIssue();
        issue.id = UUID.randomUUID();
        issue.syncRunId = syncRunId;
        issue.code = code;
        issue.message = message;
        issue.empId = empId;
        issue.empCcgid = empCcgid;
        issue.positionId = positionId;
        issue.pl3Code = pl3Code;
        issue.sourceRow = sourceRow;
        issue.createdAt = createdAt;
        return issue;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSyncRunId() {
        return syncRunId;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getEmpId() {
        return empId;
    }

    public String getEmpCcgid() {
        return empCcgid;
    }

    public String getPositionId() {
        return positionId;
    }

    public String getPl3Code() {
        return pl3Code;
    }

    public Integer getSourceRow() {
        return sourceRow;
    }
}
