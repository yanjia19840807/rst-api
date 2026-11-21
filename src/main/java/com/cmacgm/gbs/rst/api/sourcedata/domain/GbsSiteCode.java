package com.cmacgm.gbs.rst.api.sourcedata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Admin map from an SKPI office name to a Timesheet site code and center.
 */
@Entity
@Table(name = "gbs_site_code")
public class GbsSiteCode {

    @Id
    @Column(name = "gbs_site", nullable = false, length = 80)
    private String gbsSite;

    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;

    @Column(nullable = false, length = 120)
    private String center;

    protected GbsSiteCode() {
    }

    /**
     * @param gbsSite office name
     * @param siteCode 5-character site
     * @param center GBS center
     * @return row
     */
    public static GbsSiteCode create(String gbsSite, String siteCode, String center) {
        GbsSiteCode row = new GbsSiteCode();
        row.gbsSite = gbsSite;
        row.siteCode = siteCode;
        row.center = center;
        return row;
    }

    /**
     * @param siteCode 5-character site
     * @param center GBS center
     */
    public void replace(String siteCode, String center) {
        this.siteCode = siteCode;
        this.center = center;
    }

    public String getGbsSite() {
        return gbsSite;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getCenter() {
        return center;
    }
}
