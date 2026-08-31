package com.cmacgm.gbs.rst.api.security.dev;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dev identity ({@code application-dev.yml}).
 *
 * <p>When {@code override-enabled} is {@code true}, the SPA supplies CCGID / role /
 * Center on each request. Headerless calls default to {@code ADMIN001} / {@code ADMIN}.
 */
@ConfigurationProperties(prefix = "app.security.dev-identity")
public class DevIdentityProperties {

    /**
     * Fallback CCGID when the SPA does not send {@code X-Dev-Ccgid}.
     */
    private String ccgid = "ADMIN001";

    /**
     * Fallback RST role when the SPA does not send {@code X-Dev-Role}.
     */
    private String role = "ADMIN";

    /**
     * Fallback GBS Center when the SPA does not send {@code X-Dev-Center}.
     */
    private String center;

    /**
     * When {@code true}, request headers {@code X-Dev-Ccgid} / {@code X-Dev-Role} /
     * {@code X-Dev-Center} override the configured defaults. Test-only; keep
     * {@code false} once SSO is connected.
     */
    private boolean overrideEnabled = false;

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

    public boolean isOverrideEnabled() {
        return overrideEnabled;
    }

    public void setOverrideEnabled(boolean overrideEnabled) {
        this.overrideEnabled = overrideEnabled;
    }
}
