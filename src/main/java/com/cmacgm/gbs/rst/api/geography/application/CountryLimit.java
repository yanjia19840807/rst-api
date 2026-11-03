package com.cmacgm.gbs.rst.api.geography.application;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Intersects the country sets selected by Region, Cluster, and Customer Country.
 * A null set means that filter is not applied. An empty set matches nothing.
 */
public final class CountryLimit {

    private CountryLimit() {
    }

    /**
     * @param regionCountries countries in the selected regions, or null when no region is selected
     * @param clusterCountries countries in the selected clusters, or null when no cluster is selected
     * @param customerCountries selected customer countries, or null when none are selected
     * @return intersection, or null when every input is null
     */
    public static Set<String> intersect(
            Set<String> regionCountries, Set<String> clusterCountries, Set<String> customerCountries) {
        Set<String> result = copy(regionCountries);
        result = retain(result, clusterCountries);
        result = retain(result, customerCountries);
        return result;
    }

    private static Set<String> copy(Set<String> value) {
        return value == null ? null : new LinkedHashSet<>(value);
    }

    private static Set<String> retain(Set<String> current, Set<String> next) {
        if (next == null) {
            return current;
        }
        if (current == null) {
            return new LinkedHashSet<>(next);
        }
        current.retainAll(next);
        return current;
    }
}
