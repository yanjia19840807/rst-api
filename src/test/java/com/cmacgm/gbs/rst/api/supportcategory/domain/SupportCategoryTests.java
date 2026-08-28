package com.cmacgm.gbs.rst.api.supportcategory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SupportCategoryTests {

    private static final Instant NOW = Instant.parse("2026-08-19T01:00:00Z");

    @Test
    void createStartsActiveAndRenameUpdatesName() {
        SupportCategory category = SupportCategory.create("Communication", 1, "S001", NOW);
        assertThat(category.isActive()).isTrue();
        category.rename("Ops Communication", "S001", NOW);
        assertThat(category.getName()).isEqualTo("Ops Communication");
        category.setStatus(SupportCategory.STATUS_INACTIVE, "S001", NOW);
        assertThat(category.isActive()).isFalse();
        category.reorder(4, "S001", NOW);
        assertThat(category.getDisplayOrder()).isEqualTo(4);
    }

    @Test
    void deletedCategoryRejectsEdits() {
        SupportCategory category = SupportCategory.create("Reporting", 1, "S001", NOW);
        category.softDelete("S001", NOW);
        assertThatThrownBy(() -> category.rename("Reports", "S001", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deleted");
    }
}
