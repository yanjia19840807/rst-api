package com.cmacgm.gbs.rst.api.mail.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RstMailPropertiesTests {

    @Test
    void blankRedirectIsUnset() {
        RstMailProperties settings = new RstMailProperties(true, "  ");
        assertThat(settings.redirectTo()).isNull();
        assertThat(settings.redirectEnabled()).isFalse();
    }

    @Test
    void trimsRedirectAddress() {
        RstMailProperties settings = new RstMailProperties(true, " yanjiafelix@gmail.com ");
        assertThat(settings.redirectTo()).isEqualTo("yanjiafelix@gmail.com");
        assertThat(settings.redirectEnabled()).isTrue();
    }
}
