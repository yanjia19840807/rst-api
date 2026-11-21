package com.cmacgm.gbs.rst.api.sourcedata.application;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstCenters;
import com.cmacgm.gbs.rst.api.sourcedata.domain.GbsSiteCode;
import com.cmacgm.gbs.rst.api.sourcedata.persistence.GbsSiteCodeRepository;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * Maintains the SKPI office name to 5-character site code map.
 */
@Service
public class GbsSiteCodeService {

    private static final Pattern SITE_CODE = Pattern.compile("[A-Z0-9]{5}");

    private final GbsSiteCodeRepository sites;

    /**
     * @param sites site map
     */
    public GbsSiteCodeService(GbsSiteCodeRepository sites) {
        this.sites = sites;
    }

    /**
     * @return every office mapping
     */
    public List<GbsSiteCode> list() {
        return sites.findAllByOrderByGbsSiteAsc();
    }

    /**
     * Creates or replaces one office mapping. The office name is the key.
     *
     * @param gbsSite SKPI office
     * @param siteCode 5-character site
     * @param center GBS center
     * @return saved row
     */
    @Transactional
    public GbsSiteCode save(String gbsSite, String siteCode, String center) {
        String office = SourceValues.upper(gbsSite, 80);
        String code = SourceValues.upper(siteCode, 5);
        String canonical = RstCenters.canonicalize(center);
        if (office == null) {
            throw bad("GBS site is required.");
        }
        if (code == null || !SITE_CODE.matcher(code).matches()) {
            throw bad("Site code must be 5 letters or digits.");
        }
        if (canonical == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, TimesheetSyncErrorCode.UNKNOWN_CENTER.code(), "Unknown center.");
        }
        GbsSiteCode row = sites.findById(office).orElseGet(() -> GbsSiteCode.create(office, code, canonical));
        row.replace(code, canonical);
        return sites.save(row);
    }

    /**
     * @param gbsSite SKPI office
     */
    @Transactional
    public void delete(String gbsSite) {
        String office = SourceValues.upper(gbsSite, 80);
        if (office == null || !sites.existsById(office)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Site mapping not found.");
        }
        sites.deleteById(office);
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, TimesheetSyncErrorCode.INVALID_HEADER.code(), message);
    }
}
