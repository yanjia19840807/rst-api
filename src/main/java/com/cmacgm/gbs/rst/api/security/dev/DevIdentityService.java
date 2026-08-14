package com.cmacgm.gbs.rst.api.security.dev;

import java.util.List;
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
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSnapshotRowRepository;

/**
 * Resolves a configured CCGID into an {@link RstPrincipal} without persisting a local user row.
 * Display names are taken from the ACTIVE Timesheet snapshot when available.
 */
@Service
@Profile({"dev", "test"})
public class DevIdentityService {

    private static final Logger log = LoggerFactory.getLogger(DevIdentityService.class);

    private final TimesheetSnapshotRowRepository snapshotRows;
    private final ConcurrentHashMap<String, RstPrincipal> cache = new ConcurrentHashMap<>();

    public DevIdentityService(TimesheetSnapshotRowRepository snapshotRows) {
        this.snapshotRows = snapshotRows;
    }

    /**
     * Returns a principal for the given CCGID.
     *
     * @param ccgid corporate identity from config or header
     * @param roles role codes attached to the principal
     * @return resolved principal
     */
    public RstPrincipal resolve(String ccgid, Set<String> roles) {
        if (ccgid == null || ccgid.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "dev-identity-missing",
                    "app.security.dev-identity.ccgid is not configured.");
        }
        String key = ccgid.trim().toUpperCase(Locale.ROOT);
        String cacheKey = key + "|" + roles.stream().sorted().collect(Collectors.joining(","));
        return cache.computeIfAbsent(cacheKey, ignored -> load(key, roles));
    }

    private RstPrincipal load(String ccgid, Set<String> roles) {
        String displayName = firstNonBlank(
                snapshotRows.findEmployeeNamesByCcgid(ccgid),
                snapshotRows.findSupervisorNamesByCcgid(ccgid),
                snapshotRows.findSrManagerNamesByCcgid(ccgid),
                snapshotRows.findDomainHeadNamesByCcgid(ccgid));
        if (displayName == null) {
            displayName = "Dev User " + ccgid;
            log.warn(
                    "CCG ID {} not found in ACTIVE Timesheet; using synthetic display name for dev login",
                    ccgid);
        }
        String email = ccgid.toLowerCase(Locale.ROOT) + "@dev.local";
        log.info("Dev identity ready: ccgid={} name={} roles={}", ccgid, displayName, roles);
        return new RstPrincipal(ccgid, displayName, email, roles, Set.of("TIMESHEET", "SELF"));
    }

    @SafeVarargs
    private static String firstNonBlank(List<String>... groups) {
        for (List<String> group : groups) {
            for (String value : group) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }
}
