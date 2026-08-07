package com.cmacgm.gbs.rst.api.security.dev;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dev identity selection ({@code application-dev.yml}). Point {@code ccgid} at a real
 * ACTIVE Timesheet person (usually a {@code supervisor_ccgid}).
 */
@ConfigurationProperties(prefix = "app.security.dev-identity")
public class DevIdentityProperties {

    /**
     * Default walkthrough identity CCGID (loaded when {@code X-Dev-Role} is absent).
     */
    private String ccgid = "SUPERVISOR001";

    /**
     * Default role label: {@code SUPERVISOR} or {@code AGENT}.
     */
    private String role = "SUPERVISOR";

    /**
     * Optional Agent CCGID used when request sends {@code X-Dev-Role: AGENT}.
     */
    private String agentCcgid = "AGENT001";

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

    public String getAgentCcgid() {
        return agentCcgid;
    }

    public void setAgentCcgid(String agentCcgid) {
        this.agentCcgid = agentCcgid;
    }
}
