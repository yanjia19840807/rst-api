package com.cmacgm.gbs.rst.api.sourcedata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Which source one center uses. A center is either Timesheet or MyHR + SKPI.
 */
@Entity
@Table(name = "center_source")
public class CenterSource {

    public static final String TIMESHEET = "TIMESHEET";
    public static final String MYHR_SKPI = "MYHR_SKPI";

    @Id
    @Column(nullable = false, length = 120)
    private String center;

    @Column(name = "source_mode", nullable = false, length = 16)
    private String sourceMode;

    protected CenterSource() {
    }

    /**
     * @param center canonical GBS center
     * @param sourceMode TIMESHEET or MYHR_SKPI
     * @return row
     */
    public static CenterSource create(String center, String sourceMode) {
        CenterSource row = new CenterSource();
        row.center = center;
        row.sourceMode = sourceMode;
        return row;
    }

    /**
     * @param sourceMode TIMESHEET or MYHR_SKPI
     */
    public void setSourceMode(String sourceMode) {
        this.sourceMode = sourceMode;
    }

    public String getCenter() {
        return center;
    }

    public String getSourceMode() {
        return sourceMode;
    }
}
