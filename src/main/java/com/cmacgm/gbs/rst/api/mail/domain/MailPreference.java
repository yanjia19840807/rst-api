package com.cmacgm.gbs.rst.api.mail.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Explicit opt-in/out. Missing row means enabled.
 */
@Entity
@Table(name = "mail_preference")
public class MailPreference {

    @EmbeddedId
    private Pk id;

    @Column(nullable = false)
    private boolean enabled;

    protected MailPreference() {
    }

    /**
     * @param ccgid owner
     * @param mailType slug
     * @param enabled switch
     * @return row
     */
    public static MailPreference of(String ccgid, String mailType, boolean enabled) {
        MailPreference row = new MailPreference();
        row.id = new Pk(ccgid, mailType);
        row.enabled = enabled;
        return row;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCcgid() {
        return id == null ? null : id.getCcgid();
    }

    public String getMailType() {
        return id == null ? null : id.getMailType();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Composite key.
     */
    @Embeddable
    public static class Pk implements Serializable {

        @Column(length = 32)
        private String ccgid;

        @Column(name = "mail_type", length = 40)
        private String mailType;

        public Pk() {
        }

        public Pk(String ccgid, String mailType) {
            this.ccgid = ccgid;
            this.mailType = mailType;
        }

        public String getCcgid() {
            return ccgid;
        }

        public String getMailType() {
            return mailType;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(ccgid, pk.ccgid) && Objects.equals(mailType, pk.mailType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ccgid, mailType);
        }
    }
}
