package com.cmacgm.gbs.rst.api.domainhead.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class CenterLthTests {

    @Test
    void createAndReplaceKeepCenter() {
        Instant t0 = Instant.parse("2026-09-16T06:00:00Z");
        Instant t1 = Instant.parse("2026-09-16T07:00:00Z");
        CenterLth row = CenterLth.create("GBS CHINA", "POS-1", "S0001", t0);
        assertThat(row.getCenter()).isEqualTo("GBS CHINA");
        assertThat(row.getPositionId()).isEqualTo("POS-1");
        row.replace("POS-2", "S0002", t1);
        assertThat(row.getCenter()).isEqualTo("GBS CHINA");
        assertThat(row.getPositionId()).isEqualTo("POS-2");
        assertThat(row.getUpdatedBy()).isEqualTo("S0002");
        assertThat(row.getUpdatedAt()).isEqualTo(t1);
    }
}
