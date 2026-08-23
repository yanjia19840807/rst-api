package com.cmacgm.gbs.rst.api.security.dev;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;

/**
 * Resolves a configured CCGID into an {@link RstPrincipal} without persisting a local user row.
 * Display names are taken from the ACTIVE Daily Timesheet snapshot when available.
 */
@Service
@Profile({"dev", "test"})
public class DevIdentityService {

    private static final Logger log = LoggerFactory.getLogger(DevIdentityService.class);

    private final TimesheetReadService timesheet;
    private final ConcurrentHashMap<String, RstPrincipal> cache = new ConcurrentHashMap<>();

    public DevIdentityService(TimesheetReadService timesheet) {
        this.timesheet = timesheet;
    }

    /**
     * Returns a principal for the given CCGID.
     *
     * @param ccgid corporate identity from config or header
     * @param roles role codes attached to the principal
     * @param center GBS Center from config or {@code X-Dev-Center}
     * @return resolved principal
     */
    public RstPrincipal resolve(String ccgid, Set<String> roles, String center) {
        if (ccgid == null || ccgid.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "dev-identity-missing",
                    "app.security.dev-identity.ccgid is not configured.");
        }
        String key = ccgid.trim().toUpperCase(Locale.ROOT);
        String resolvedCenter = center == null || center.isBlank() ? null : center.trim();
        String cacheKey = key + "|" + roles.stream().sorted().collect(Collectors.joining(","))
                + "|" + (resolvedCenter == null ? "" : resolvedCenter);
        return cache.computeIfAbsent(cacheKey, ignored -> load(key, roles, resolvedCenter));
    }

    private RstPrincipal load(String ccgid, Set<String> roles, String center) {
        String displayName = timesheet.findDisplayName(ccgid).orElse(null);
        if (displayName == null) {
            displayName = "Dev User " + ccgid;
            log.warn(
                    "CCG ID {} not found in ACTIVE Daily Timesheet; using synthetic display name for dev login",
                    ccgid);
        }
        String email = ccgid.toLowerCase(Locale.ROOT) + "@dev.local";
        log.info("Dev identity ready: ccgid={} name={} roles={} center={}", ccgid, displayName, roles, center);
        return new RstPrincipal(ccgid, displayName, email, roles, Set.of("TIMESHEET", "SELF"), center);
    }
}
