package com.cmacgm.gbs.rst.api.tms.application;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.governance.application.CommaTokens;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitResponse;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitResponse.SharedKpiResponse;

/**
 * Resolves Toolkit IDs for TMS list filters on live Toolkit scope fields.
 */
final class TmsToolkitScope {

    private TmsToolkitScope() {
    }

    static boolean hasScopeFilter(
            UUID toolkitId,
            String center,
            String domain,
            String pl3Code,
            String carrier,
            String site,
            String customerCountry) {
        return toolkitId != null
                || hasText(center)
                || hasText(domain)
                || hasText(pl3Code)
                || hasText(carrier)
                || hasText(site)
                || hasText(customerCountry);
    }

    static List<UUID> matchingIds(
            List<ToolkitResponse> toolkits,
            Collection<UUID> scopedIds,
            UUID toolkitId,
            String center,
            String domain,
            String pl3Code,
            String carrier,
            String site,
            String customerCountry) {
        if (toolkits == null || toolkits.isEmpty()) {
            return List.of();
        }
        return toolkits.stream()
                .filter(toolkit -> scopedIds == null || scopedIds.contains(toolkit.id()))
                .filter(toolkit -> toolkitId == null || toolkitId.equals(toolkit.id()))
                .filter(toolkit -> !hasText(center) || center.equals(toolkit.center()))
                .filter(toolkit -> !hasText(domain) || domain.equals(toolkit.domain()))
                .filter(toolkit -> !hasText(pl3Code) || pl3Code.equals(toolkit.pl3Code()))
                .filter(toolkit -> matchesKpi(toolkit, carrier, site, customerCountry))
                .map(ToolkitResponse::id)
                .toList();
    }

    private static boolean matchesKpi(
            ToolkitResponse toolkit, String carrier, String site, String customerCountry) {
        if (!hasText(carrier) && !hasText(site) && !hasText(customerCountry)) {
            return true;
        }
        List<SharedKpiResponse> selections = toolkit.sharedKpiSelections();
        if (selections == null || selections.isEmpty()) {
            return false;
        }
        return selections.stream().anyMatch(selection ->
                (!hasText(carrier) || carrier.equals(selection.carrier()))
                        && (!hasText(site) || site.equals(selection.site()))
                        && (!hasText(customerCountry)
                                || CommaTokens.contains(selection.customerCountry(), customerCountry)));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
