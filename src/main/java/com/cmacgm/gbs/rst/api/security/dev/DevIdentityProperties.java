package com.cmacgm.gbs.rst.api.security.dev;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dev identity selection ({@code application-dev.yml}).
 *
 * <p>Set {@code ccgid} + {@code role} to simulate one user login. Allowed roles:
 * {@code AGENT}, {@code SUPERVISOR}, {@code MANAGER}, {@code CDH}, {@code LTH}, {@code HO}.
 */
@ConfigurationProperties(prefix = "app.security.dev-identity")
public class DevIdentityProperties {

    /**
     * Simulated user CCGID.
     */
    private String ccgid = "SUPERVISOR001";

    /**
     * Simulated RST role (one of the six product roles).
     */
    private String role = "SUPERVISOR";

    /**
     * Simulated GBS Center. SSO will later supply the same field from the Center claim.
     */
    private String center = "Kuala Lumpur";

    public String getCcgid() {
        return ccgid;
    }

    public void setCcgid(String ccgid) {
        this.ccgid = ccgid;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCenter() {
        return center;
    }

    public void setCenter(String center) {
        this.center = center;
    }
}
