package com.cmacgm.gbs.rst.api.security.sso;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.cmacgm.gbs.rst.api.security.RstCenters;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.RstRoles;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Builds an {@link RstPrincipal} from a validated Azure ID token.
 */
@Component
@Profile({"uat", "pre", "prod"})
public class SsoUserResolver {

    private final TimesheetReadService timesheet;
    private final SsoProperties properties;

    /**
     * @param timesheet ACTIVE Daily seats for SSO USER
     * @param properties current SSO env
     */
    public SsoUserResolver(TimesheetReadService timesheet, SsoProperties properties) {
        this.timesheet = timesheet;
        this.properties = properties;
    }

    /**
     * @param jwt validated ID token
     * @return product principal
     */
    public RstPrincipal resolve(Jwt jwt) {
        String ccgid = firstClaim(jwt, "CCGID", "ccgid");
        if (ccgid == null) {
            throw new SsoException("sso-ccgid-missing", "CCGID is missing from the sign-in token.");
        }
        ccgid = ccgid.trim().toUpperCase(Locale.ROOT);
        SsoRoleParser.Parsed parsed = SsoRoleParser.parse(firstRole(jwt), properties.env());
        String displayName = firstClaim(jwt, "name", "preferred_username");
        String email = firstClaim(jwt, "email", "preferred_username");
        return switch (parsed.name()) {
            case "USER" -> resolveUser(ccgid, displayName, email);
            case "LOCAL_TRANSFORMATION_HEAD" -> new RstPrincipal(
                    ccgid,
                    displayName,
                    email,
                    Set.of(RstRoles.LOCAL_TRANSFORMATION_HEAD),
                    Set.of(),
                    requireCenter(jwt));
            case "GOVERNANCE" -> new RstPrincipal(
                    ccgid, displayName, email, Set.of(RstRoles.GOVERNANCE), Set.of(), null);
            case "ADMIN" -> new RstPrincipal(
                    ccgid, displayName, email, Set.of(RstRoles.ADMIN), Set.of(), null);
            default -> throw new SsoException("sso-role-invalid", "SSO role is not a RST application role.");
        };
    }

    private RstPrincipal resolveUser(String ccgid, String displayName, String email) {
        TimesheetReadService.ProductSeat seat = timesheet.findActiveProductSeat(ccgid)
                .orElseThrow(() -> new SsoException(
                        "sso-timesheet-missing",
                        "CCGID is not in the ACTIVE Daily Timesheet."));
        String role = seat.roleType() == null ? "" : seat.roleType().trim().toUpperCase(Locale.ROOT);
        if (!RstRoles.TIMESHEET_USER_ROLES.contains(role)) {
            throw new SsoException(
                    "sso-timesheet-role",
                    "Timesheet role is not AGENT, SUPERVISOR, SR_MANAGER, or DOMAIN_HEAD.");
        }
        String center = RstCenters.canonicalize(seat.center());
        if (center == null) {
            throw new SsoException(
                    "sso-center-invalid",
                    "Timesheet center is missing or is not a GBS China center.");
        }
        return new RstPrincipal(
                ccgid,
                firstNonBlank(seat.displayName(), displayName),
                firstNonBlank(seat.email(), email),
                Set.of(role),
                Set.of(),
                center);
    }

    private static String requireCenter(Jwt jwt) {
        String center = RstCenters.canonicalize(firstClaim(jwt, "center", "Center"));
        if (center == null) {
            throw new SsoException("sso-center-invalid", "Center is missing or is not a GBS China center.");
        }
        return center;
    }

    private static String firstRole(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || roles.isEmpty()) {
            String single = jwt.getClaimAsString("roles");
            return single;
        }
        return roles.getFirst();
    }

    private static String firstClaim(Jwt jwt, String first, String second) {
        return firstNonBlank(jwt.getClaimAsString(first), jwt.getClaimAsString(second));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? null : second.trim();
    }
}
