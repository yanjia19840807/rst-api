package com.cmacgm.gbs.rst.api.sourcedata.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstCenters;
import com.cmacgm.gbs.rst.api.sourcedata.domain.CenterSource;
import com.cmacgm.gbs.rst.api.sourcedata.persistence.CenterSourceRepository;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncErrorCode;

/**
 * Reads and updates which centers use Timesheet and which use MyHR + SKPI.
 */
@Service
public class CenterSourceService {

    private final CenterSourceRepository sources;

    /**
     * @param sources center source rows
     */
    public CenterSourceService(CenterSourceRepository sources) {
        this.sources = sources;
    }

    /**
     * @return every configured center
     */
    public List<CenterSource> list() {
        return sources.findAll();
    }

    /**
     * @param center GBS center
     * @return configured row
     */
    public CenterSource require(String center) {
        String canonical = RstCenters.canonicalize(center);
        if (canonical == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, TimesheetSyncErrorCode.UNKNOWN_CENTER.code(), "Unknown center.");
        }
        return sources.findById(canonical)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, TimesheetSyncErrorCode.UNKNOWN_CENTER.code(), "Unknown center."));
    }

    /**
     * @param center GBS center
     * @return TIMESHEET when the center is unknown or configured that way
     */
    public String sourceMode(String center) {
        String canonical = RstCenters.canonicalize(center);
        if (canonical == null) {
            return CenterSource.TIMESHEET;
        }
        return sources.findById(canonical)
                .map(CenterSource::getSourceMode)
                .orElse(CenterSource.TIMESHEET);
    }

    /**
     * @param mode TIMESHEET or MYHR_SKPI
     * @return canonical centers using that mode
     */
    public Set<String> centersWith(String mode) {
        return sources.findAll().stream()
                .filter(row -> mode.equals(row.getSourceMode()))
                .map(CenterSource::getCenter)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Sets the source mode. Switching does not delete the other source's snapshots.
     *
     * @param center GBS center
     * @param mode TIMESHEET or MYHR_SKPI
     * @return saved row
     */
    @Transactional
    public CenterSource update(String center, String mode) {
        String canonical = RstCenters.canonicalize(center);
        if (canonical == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, TimesheetSyncErrorCode.UNKNOWN_CENTER.code(), "Unknown center.");
        }
        String normalized = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (!CenterSource.TIMESHEET.equals(normalized) && !CenterSource.MYHR_SKPI.equals(normalized)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    TimesheetSyncErrorCode.INVALID_HEADER.code(),
                    "Source mode must be TIMESHEET or MYHR_SKPI.");
        }
        CenterSource row = sources.findById(canonical)
                .orElseGet(() -> CenterSource.create(canonical, normalized));
        row.setSourceMode(normalized);
        return sources.save(row);
    }
}
