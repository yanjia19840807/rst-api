package com.cmacgm.gbs.rst.api.security.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SsoRoleParserTests {

    @Test
    void parsesCanonicalNames() {
        assertThat(SsoRoleParser.parse("CMACGM_APP_RST_USER_UAT", "UAT"))
                .isEqualTo(new SsoRoleParser.Parsed("USER", "UAT"));
        assertThat(SsoRoleParser.parse("CMACGM_APP_RST_LOCAL_TRANSFORMATION_HEAD_PRE", "PRE"))
                .isEqualTo(new SsoRoleParser.Parsed("LOCAL_TRANSFORMATION_HEAD", "PRE"));
        assertThat(SsoRoleParser.parse("CMACGM_APP_RST_GOVERNANCE_PROD", "prod"))
                .isEqualTo(new SsoRoleParser.Parsed("GOVERNANCE", "PROD"));
        assertThat(SsoRoleParser.parse("CMACGM_APP_RST_ADMIN_UAT", "UAT"))
                .isEqualTo(new SsoRoleParser.Parsed("ADMIN", "UAT"));
    }

    @Test
    void rejectsEnvMismatchAndUnknownName() {
        assertThatThrownBy(() -> SsoRoleParser.parse("CMACGM_APP_RST_ADMIN_PROD", "UAT"))
                .isInstanceOf(SsoException.class)
                .extracting(ex -> ((SsoException) ex).code())
                .isEqualTo("sso-env-mismatch");
        assertThatThrownBy(() -> SsoRoleParser.parse("CMACGM_APP_RST_LTH_UAT", "UAT"))
                .isInstanceOf(SsoException.class)
                .extracting(ex -> ((SsoException) ex).code())
                .isEqualTo("sso-role-invalid");
        assertThatThrownBy(() -> SsoRoleParser.parse("CMACGM_APP_RST_ADMIN_DEV", "DEV"))
                .isInstanceOf(SsoException.class);
    }
}
