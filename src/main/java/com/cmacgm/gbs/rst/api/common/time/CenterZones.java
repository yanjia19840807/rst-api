package com.cmacgm.gbs.rst.api.common.time;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstCenters;

/**
 * Canonical IANA timezone for each GBS Center. Never falls back to the JVM default.
 */
public final class CenterZones {

    private static final Map<String, ZoneId> ZONES = Map.of(
            RstCenters.GBS_CHINA, ZoneId.of("Asia/Shanghai"),
            RstCenters.GBS_INDIA, ZoneId.of("Asia/Kolkata"),
            RstCenters.GBS_LEBANON, ZoneId.of("Asia/Beirut"),
            RstCenters.GBS_ESTONIA, ZoneId.of("Europe/Tallinn"),
            RstCenters.GBS_COSTA_RICA, ZoneId.of("America/Costa_Rica"),
            RstCenters.GBS_PHILIPPINES, ZoneId.of("Asia/Manila"),
            RstCenters.GBS_PORTUGAL, ZoneId.of("Europe/Lisbon"));

    private static final List<String> ORDER = List.of(
            RstCenters.GBS_CHINA,
            RstCenters.GBS_INDIA,
            RstCenters.GBS_LEBANON,
            RstCenters.GBS_ESTONIA,
            RstCenters.GBS_COSTA_RICA,
            RstCenters.GBS_PHILIPPINES,
            RstCenters.GBS_PORTUGAL);

    private CenterZones() {
    }

    /**
     * Stable Center → IANA catalog for the SPA.
     *
     * @return entries in display order
     */
    public static List<CenterZone> catalog() {
        return ORDER.stream()
                .map(center -> new CenterZone(center, ZONES.get(center).getId()))
                .toList();
    }

    /**
     * One catalog row.
     *
     * @param center canonical GBS Center
     * @param timeZone IANA zone
     */
    public record CenterZone(String center, String timeZone) {
    }

    /**
     * @param center raw or canonical Center name
     * @return IANA zone for that Center
     */
    public static ZoneId of(String center) {
        String canonical = RstCenters.canonicalize(center);
        if (canonical == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "unknown-center-timezone",
                    "No IANA timezone is configured for Center: " + center + ".");
        }
        return ZONES.get(canonical);
    }

    /**
     * @param center raw or canonical Center name
     * @return true when the Center has a configured IANA zone
     */
    public static boolean isKnown(String center) {
        return RstCenters.canonicalize(center) != null;
    }
}
