package com.cmacgm.gbs.rst.api.exercise.associateddata.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cmacgm.gbs.rst.api.toolkit.application.ToolkitVolumeService.VolumeSeed;

class ToolkitVolumeGridTests {

    @Test
    void monthlyOverlayCopiesWindowAndFillsHoles() {
        Map<LocalDate, VolumeSeed> seed = new LinkedHashMap<>();
        seed.put(LocalDate.of(2020, 1, 1), new VolumeSeed(new BigDecimal("10"), null));
        seed.put(LocalDate.of(2026, 1, 1), new VolumeSeed(new BigDecimal("100"), null));
        seed.put(LocalDate.of(2026, 3, 1), new VolumeSeed(new BigDecimal("120"), null));
        var rows = ToolkitVolumeGrid.monthlyOverlay(seed, LocalDate.of(2026, 9, 1));
        assertThat(rows).extracting(row -> row.month())
                .containsExactly("2026-01", "2026-02", "2026-03");
        assertThat(rows.get(0).actualVolume()).isEqualByComparingTo("100");
        assertThat(rows.get(1).actualVolume()).isNull();
        assertThat(rows.get(2).actualVolume()).isEqualByComparingTo("120");
    }

    @Test
    void monthlyOverlayEmptyWhenToolkitHasNothingInWindow() {
        Map<LocalDate, VolumeSeed> seed = new LinkedHashMap<>();
        seed.put(LocalDate.of(2020, 1, 1), new VolumeSeed(new BigDecimal("10"), null));
        assertThat(ToolkitVolumeGrid.monthlyOverlay(seed, LocalDate.of(2026, 9, 1))).isEmpty();
    }
}
