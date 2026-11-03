package com.cmacgm.gbs.rst.api.geography.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class CountryLimitTests {

    @Test
    void noSelectionLeavesTheFilterOpen() {
        assertThat(CountryLimit.intersect(null, null, null)).isNull();
    }

    @Test
    void intersectsRegionAndCluster() {
        assertThat(CountryLimit.intersect(Set.of("FR", "CN"), Set.of("CN", "JP"), null))
                .containsExactly("CN");
    }

    @Test
    void emptyIntersectionStaysEmpty() {
        assertThat(CountryLimit.intersect(Set.of("FR"), Set.of("JP"), null)).isEmpty();
    }

    @Test
    void customerCountryNarrowsTheGroup() {
        assertThat(CountryLimit.intersect(Set.of("FR", "CN"), null, Set.of("FR")))
                .containsExactly("FR");
    }
}
