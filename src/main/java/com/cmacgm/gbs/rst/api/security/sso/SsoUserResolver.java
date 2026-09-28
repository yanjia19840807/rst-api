package com.cmacgm.gbs.rst.api.security.sso;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.cmacgm.gbs.rst.api.domainhead.application.DomainHeadConfigService;
import com.cmacgm.gbs.rst.api.security.RstCenters;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.RstRoles;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Builds an {@link RstPrincipal} from a validated Azure ID token.
 * All matching App Roles are unioned; {@code USER} expands Timesheet / Center Roles.
 */
@Component
@Profile({"uat", "pre", "prod"})
public class SsoUserResolver {

    private final TimesheetReadService timesheet;
    private final SsoProperties properties;
    private final DomainHeadConfigService domainHeads;

    /**
     * @param timesheet ACTIVE Daily seats for SSO USER
     * @param properties current SSO env
     * @param domainHeads Center Roles CDH / LTH assignments
     */
    public SsoUserResolver(
            TimesheetReadService timesheet,
            SsoProperties properties,
            DomainHeadConfigService domainHeads) {
        this.timesheet = timesheet;
        this.properties = properties;
        this.domainHeads = domainHeads;
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
        Set<String> ssoNames = SsoRoleParser.parseNames(roleClaims(jwt), properties.env());
        String tokenName = firstClaim(jwt, "name", "preferred_username");
        String tokenEmail = firstClaim(jwt, "email", "preferred_username");

        Set<String> productRoles = new LinkedHashSet<>();
        if (ssoNames.contains("LOCAL_TRANSFORMATION_HEAD")) {
            productRoles.add(RstRoles.LOCAL_TRANSFORMATION_HEAD);
        }
        if (ssoNames.contains("GOVERNANCE")) {
            productRoles.add(RstRoles.GOVERNANCE);
        }
        if (ssoNames.contains("ADMIN")) {
            productRoles.add(RstRoles.ADMIN);
        }

        UserExpansion user = null;
        if (ssoNames.contains("USER")) {
            user = expandUser(ccgid);
            productRoles.addAll(user.roles());
        }

        if (productRoles.isEmpty()) {
            if (user != null && user.failureCode() != null) {
                throw new SsoException(user.failureCode(), user.failureMessage());
            }
            throw new SsoException("sso-role-missing", "SSO role is missing.");
        }

        String center = resolveCenter(jwt, productRoles, user);
        return new RstPrincipal(
                ccgid,
                firstNonBlank(user == null ? null : user.displayName(), tokenName),
                firstNonBlank(user == null ? null : user.email(), tokenEmail),
                Set.copyOf(productRoles),
                Set.of(),
                center);
    }

    private UserExpansion expandUser(String ccgid) {
        TimesheetReadService.ProductSeat seat = timesheet.findActiveProductSeat(ccgid).orElse(null);
        Set<String> roles = new LinkedHashSet<>();
        if (seat != null && seat.roleTypes() != null) {
            for (String roleType : seat.roleTypes()) {
                if (roleType == null || roleType.isBlank()) {
                    continue;
                }
                String role = roleType.trim().toUpperCase(Locale.ROOT);
                if (RstRoles.TIMESHEET_USER_ROLES.contains(role)) {
                    roles.add(role);
                }
            }
        }
        if (domainHeads.isAssignedCdh(ccgid)) {
            roles.add(RstRoles.DOMAIN_HEAD);
        }
        if (domainHeads.isAssignedLth(ccgid)) {
            roles.add(RstRoles.LOCAL_TRANSFORMATION_HEAD);
        }

        TimesheetPerson person = seat == null ? timesheet.findActivePerson(ccgid).orElse(null) : null;
        String center = RstCenters.canonicalize(
                seat != null ? seat.center() : person == null ? null : person.getCenter());
        String displayName = seat != null
                ? seat.displayName()
                : person == null ? null : person.getName();
        String email = seat != null ? seat.email() : person == null ? null : person.getEmail();

        if (!roles.isEmpty()) {
            if (center == null) {
                throw new SsoException(
                        "sso-center-invalid",
                        "Timesheet center is missing or is not a known GBS Center.");
            }
            return new UserExpansion(Set.copyOf(roles), center, displayName, email, null, null);
        }
        if (seat == null) {
            return new UserExpansion(
                    Set.of(),
                    center,
                    displayName,
                    email,
                    "sso-timesheet-missing",
                    "CCGID is not in the ACTIVE Daily Timesheet.");
        }
        return new UserExpansion(
                Set.of(),
                center,
                displayName,
                email,
                "sso-timesheet-role",
                "Timesheet role is not AGENT, SUPERVISOR, SR_MANAGER, or DOMAIN_HEAD,"
                        + " and Center Roles has no CDH/LTH assignment.");
    }

    private static String resolveCenter(Jwt jwt, Set<String> productRoles, UserExpansion user) {
        if (user != null && user.center() != null) {
            return user.center();
        }
        if (productRoles.contains(RstRoles.LOCAL_TRANSFORMATION_HEAD)) {
            return requireCenter(jwt);
        }
        return null;
    }

    private static String requireCenter(Jwt jwt) {
        String center = RstCenters.canonicalize(firstClaim(jwt, "center", "Center"));
        if (center == null) {
            throw new SsoException("sso-center-invalid", "Center is missing or is not a known GBS Center.");
        }
        return center;
    }

    private static List<String> roleClaims(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null && !roles.isEmpty()) {
            return roles;
        }
        String single = jwt.getClaimAsString("roles");
        if (single == null || single.isBlank()) {
            return List.of();
        }
        List<String> one = new ArrayList<>(1);
        one.add(single);
        return one;
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

    private record UserExpansion(
            Set<String> roles,
            String center,
            String displayName,
            String email,
            String failureCode,
            String failureMessage) {
    }
}
