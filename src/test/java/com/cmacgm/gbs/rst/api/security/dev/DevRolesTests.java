package com.cmacgm.gbs.rst.api.security.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DevRolesTests {

    @Test
    void acceptsCanonicalRoles() {
        assertThat(DevRoles.requireValid("local_transformation_head"))
                .isEqualTo("LOCAL_TRANSFORMATION_HEAD");
    }

    @Test
    void rejectsUnknownRoles() {
        assertThatThrownBy(() -> DevRoles.requireValid("USER"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DevRoles.requireValid("LTH"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DevRoles.requireValid("MANAGER"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
