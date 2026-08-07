package com.cmacgm.gbs.rst.api.security.dev;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.identity.domain.AppUser;
import com.cmacgm.gbs.rst.api.identity.persistence.AppUserRepository;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSnapshotRowRepository;

/**
 * Resolves a configured CCGID into an {@link RstPrincipal}, ensuring a matching {@code app_user}
 * exists. Display names are taken from {@code app_user} or the ACTIVE Timesheet snapshot.
 */
@Service
@Profile({"dev", "test"})
public class DevIdentityService {

    private static final Logger log = LoggerFactory.getLogger(DevIdentityService.class);

    private final AppUserRepository users;
    private final TimesheetSnapshotRowRepository snapshotRows;
    private final Clock clock;
    private final ConcurrentHashMap<String, RstPrincipal> cache = new ConcurrentHashMap<>();

    /**
     * @param users app user repository
     * @param snapshotRows ACTIVE timesheet rows
     * @param clock clock for new user timestamps
     */
    public DevIdentityService(
            AppUserRepository users,
            TimesheetSnapshotRowRepository snapshotRows,
            Clock clock) {
        this.users = users;
        this.snapshotRows = snapshotRows;
        this.clock = clock;
    }

    /**
     * Returns a principal for the given CCGID, creating {@code app_user} when missing.
     *
     * @param ccgid corporate identity from config or header
     * @param roles role codes attached to the principal
     * @return resolved principal
     */
    @Transactional
    public RstPrincipal resolve(String ccgid, Set<String> roles) {
        if (ccgid == null || ccgid.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "dev-identity-missing",
                    "app.security.dev-identity.ccgid is not configured.");
        }
        String key = ccgid.trim().toUpperCase(Locale.ROOT);
        return cache.computeIfAbsent(key, ignored -> loadOrCreate(key, roles));
    }

    private RstPrincipal loadOrCreate(String ccgid, Set<String> roles) {
        AppUser user = users.findByCcgidAndActiveTrue(ccgid).orElseGet(() -> createFromTimesheet(ccgid));
        log.info("Dev identity ready: ccgid={} userId={} name={}",
                user.getCcgid(), user.getId(), user.getDisplayName());
        return new RstPrincipal(
                user.getId(),
                user.getCcgid(),
                user.getDisplayName(),
                user.getEmail(),
                roles,
                Set.of("TIMESHEET", "SELF"));
    }

    private AppUser createFromTimesheet(String ccgid) {
        String displayName = firstNonBlank(
                snapshotRows.findSupervisorNamesByCcgid(ccgid),
                snapshotRows.findEmployeeNamesByCcgid(ccgid));
        if (displayName == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "dev-identity-not-in-timesheet",
                    "CCG ID " + ccgid
                            + " was not found in app_user or ACTIVE Timesheet. "
                            +                     "Set app.security.dev-identity.ccgid in application-dev.yml "
                            + "to a supervisor_ccgid from the synced Monthly Report.");
        }
        Instant now = clock.instant();
        String email = ccgid.toLowerCase(Locale.ROOT) + "@dev.local";
        AppUser created = new AppUser(UUID.randomUUID(), ccgid, displayName, email, now);
        users.save(created);
        log.info("Created app_user from Timesheet for dev identity: {} ({})", displayName, ccgid);
        return created;
    }

    private static String firstNonBlank(java.util.List<String> first, java.util.List<String> second) {
        for (String value : first) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        for (String value : second) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
