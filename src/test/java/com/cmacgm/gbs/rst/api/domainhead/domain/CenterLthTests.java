package com.cmacgm.gbs.rst.api.domainhead.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CenterLthTests {

    @Test
    void createReplaceAndClearKeepTheAuditId() {
        CenterLth row = CenterLth.create("GBS CHINA", "POS-1");
        assertThat(row.getCenter()).isEqualTo("GBS CHINA");
        assertThat(row.getPositionId()).isEqualTo("POS-1");
        assertThat(row.getId()).isNotNull();
        row.replace("POS-2");
        assertThat(row.getPositionId()).isEqualTo("POS-2");
        row.clear();
        assertThat(row.getPositionId()).isNull();
        assertThat(row.getCenter()).isEqualTo("GBS CHINA");
        assertThat(row.getId()).isNotNull();
    }
}
