package com.cmacgm.gbs.rst.api.mail.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailPropertiesTests {

    @Test
    void blankRedirectIsUnset() {
        MailProperties settings = new MailProperties(true, "  ", null);
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.redirectTo()).isNull();
        assertThat(settings.redirectEnabled()).isFalse();
        assertThat(settings.from()).isEqualTo("GBS.TIMESHEET@cma-cgm.com");
    }

    @Test
    void trimsRedirectAddress() {
        MailProperties settings = new MailProperties(true, " yanjiafelix@gmail.com ", "  ops@cma-cgm.com ");
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.redirectTo()).isEqualTo("yanjiafelix@gmail.com");
        assertThat(settings.redirectEnabled()).isTrue();
        assertThat(settings.from()).isEqualTo("ops@cma-cgm.com");
    }
}
