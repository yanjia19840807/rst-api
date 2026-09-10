package com.cmacgm.gbs.rst.api.security.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.cmacgm.gbs.rst.api.security.RstRoles;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class SsoUserResolverTests {

    @Mock
    private TimesheetReadService timesheet;

    @Test
    void userReadsTimesheetSeat() {
        when(timesheet.findActiveProductSeat("S00596242"))
                .thenReturn(Optional.of(new TimesheetReadService.ProductSeat(
                        "SUPERVISOR", "GBS CHINA INDIA", "WU Bertie", "GSC.BERWU@cma-cgm.com")));
        SsoUserResolver resolver = new SsoUserResolver(timesheet, properties("UAT"));

        var principal = resolver.resolve(token(
                "S00596242",
                List.of("CMACGM_APP_RST_USER_UAT"),
                "WU Bertie",
                "GSC.BERWU@cma-cgm.com",
                null));

        assertThat(principal.ccgid()).isEqualTo("S00596242");
        assertThat(principal.roles()).containsExactly(RstRoles.SUPERVISOR);
        assertThat(principal.center()).isEqualTo("GBS CHINA INDIA");
        assertThat(principal.displayName()).isEqualTo("WU Bertie");
    }

    @Test
    void localTransformationHeadUsesTokenCenter() {
        SsoUserResolver resolver = new SsoUserResolver(timesheet, properties("UAT"));

        var principal = resolver.resolve(token(
                "S001",
                List.of("CMACGM_APP_RST_LOCAL_TRANSFORMATION_HEAD_UAT"),
                "LTH One",
                "lth@cma-cgm.com",
                "GBS CHINA PHILIPPINES"));

        assertThat(principal.roles()).containsExactly(RstRoles.LOCAL_TRANSFORMATION_HEAD);
        assertThat(principal.center()).isEqualTo("GBS CHINA PHILIPPINES");
    }

    @Test
    void governanceAndAdminHaveNoCenter() {
        SsoUserResolver resolver = new SsoUserResolver(timesheet, properties("PROD"));

        assertThat(resolver.resolve(token(
                        "S002",
                        List.of("CMACGM_APP_RST_GOVERNANCE_PROD"),
                        "Gov",
                        "gov@cma-cgm.com",
                        null))
                .roles())
                .containsExactly(RstRoles.GOVERNANCE);
        assertThat(resolver.resolve(token(
                        "S003",
                        List.of("CMACGM_APP_RST_ADMIN_PROD"),
                        "Admin",
                        "admin@cma-cgm.com",
                        "GBS CHINA INDIA"))
                .center())
                .isNull();
    }

    @Test
    void userMissingFromTimesheetIsRejected() {
        when(timesheet.findActiveProductSeat("S009")).thenReturn(Optional.empty());
        SsoUserResolver resolver = new SsoUserResolver(timesheet, properties("UAT"));

        assertThatThrownBy(() -> resolver.resolve(token(
                        "S009",
                        List.of("CMACGM_APP_RST_USER_UAT"),
                        "Missing",
                        "m@cma-cgm.com",
                        null)))
                .isInstanceOf(SsoException.class)
                .extracting(ex -> ((SsoException) ex).code())
                .isEqualTo("sso-timesheet-missing");
    }

    @Test
    void userTimesheetRoleMustBeProductSeat() {
        when(timesheet.findActiveProductSeat("S010"))
                .thenReturn(Optional.of(new TimesheetReadService.ProductSeat(
                        "ADMIN", "GBS CHINA INDIA", "Wrong", "w@cma-cgm.com")));
        SsoUserResolver resolver = new SsoUserResolver(timesheet, properties("UAT"));

        assertThatThrownBy(() -> resolver.resolve(token(
                        "S010",
                        List.of("CMACGM_APP_RST_USER_UAT"),
                        "Wrong",
                        "w@cma-cgm.com",
                        null)))
                .isInstanceOf(SsoException.class)
                .extracting(ex -> ((SsoException) ex).code())
                .isEqualTo("sso-timesheet-role");
    }

    @Test
    void userTimesheetCenterMustBeCanonical() {
        when(timesheet.findActiveProductSeat("S011"))
                .thenReturn(Optional.of(new TimesheetReadService.ProductSeat(
                        "AGENT", "Kuala Lumpur", "Agent", "a@cma-cgm.com")));
        SsoUserResolver resolver = new SsoUserResolver(timesheet, properties("UAT"));

        assertThatThrownBy(() -> resolver.resolve(token(
                        "S011",
                        List.of("CMACGM_APP_RST_USER_UAT"),
                        "Agent",
                        "a@cma-cgm.com",
                        null)))
                .isInstanceOf(SsoException.class)
                .extracting(ex -> ((SsoException) ex).code())
                .isEqualTo("sso-center-invalid");
    }

    @Test
    void missingCcgidIsRejected() {
        SsoUserResolver resolver = new SsoUserResolver(timesheet, properties("UAT"));

        assertThatThrownBy(() -> resolver.resolve(token(
                        null,
                        List.of("CMACGM_APP_RST_USER_UAT"),
                        "No Id",
                        "n@cma-cgm.com",
                        null)))
                .isInstanceOf(SsoException.class)
                .extracting(ex -> ((SsoException) ex).code())
                .isEqualTo("sso-ccgid-missing");
    }

    @Test
    void unknownLthCenterIsRejected() {
        SsoUserResolver resolver = new SsoUserResolver(timesheet, properties("UAT"));

        assertThatThrownBy(() -> resolver.resolve(token(
                        "S001",
                        List.of("CMACGM_APP_RST_LOCAL_TRANSFORMATION_HEAD_UAT"),
                        "LTH One",
                        "lth@cma-cgm.com",
                        "Kuala Lumpur")))
                .isInstanceOf(SsoException.class)
                .extracting(ex -> ((SsoException) ex).code())
                .isEqualTo("sso-center-invalid");
    }

    private static SsoProperties properties(String env) {
        return new SsoProperties(
                "9d314297-75dc-4329-8f94-a66489b4b9bb",
                "client",
                "secret",
                "https://gbs-rst-uat.cma-cgm.com/api/sso/callback",
                env);
    }

    private static Jwt token(String ccgid, List<String> roles, String name, String email, String center) {
        var builder = Jwt.withTokenValue("id-token")
                .header("alg", "none")
                .issuer("https://login.microsoftonline.com/9d314297-75dc-4329-8f94-a66489b4b9bb/v2.0")
                .subject("sub")
                .issuedAt(Instant.parse("2026-09-10T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-10T01:00:00Z"))
                .claim("roles", roles)
                .claim("name", name)
                .claim("email", email);
        if (ccgid != null) {
            builder.claim("CCGID", ccgid);
        }
        if (center != null) {
            builder.claim("center", center);
        }
        return builder.build();
    }
}
