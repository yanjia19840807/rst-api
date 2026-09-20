package com.cmacgm.gbs.rst.api.tms.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.governance.application.CommaTokens;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSession;
import com.cmacgm.gbs.rst.api.toolkit.domain.Toolkit;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSharedKpiSelection;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitSubtask;

public record TmsSessionResponse(
        String id,
        UUID toolkitId,
        UUID subtaskId,
        String toolkitName,
        String center,
        String domain,
        String pl1,
        String pl2,
        String pl3,
        String pl3Code,
        List<String> carriers,
        List<String> sites,
        List<String> customerCountries,
        String subtaskName,
        String agentName,
        String agentCcgid,
        BigDecimal processedVolume,
        String reference,
        String remarks,
        String status,
        boolean enabled,
        Instant startedAt,
        Instant pausedAt,
        Instant endedAt,
        long netDurationSeconds,
        String discardReason,
        long version) {

    public static TmsSessionResponse from(TmsSession session, Instant now, String agentName) {
        Instant wireStartedAt = session.getRunningSince() == null
                ? session.getStartedAt()
                : session.getRunningSince();
        long wireNetDuration = session.elapsedSeconds(now);
        Toolkit toolkit = session.getToolkit();
        ToolkitSubtask subtask = session.getToolkitSubtask();
        String resolvedAgent = agentName == null || agentName.isBlank()
                ? session.getAgentCcgid()
                : agentName;
        List<ToolkitSharedKpiSelection> selections = toolkit.getSharedKpiSelections() == null
                ? List.of()
                : toolkit.getSharedKpiSelections().stream()
                        .filter(selection -> selection.getDeletedAt() == null)
                        .toList();
        return new TmsSessionResponse(
                session.getSessionNo(),
                toolkit.getId(),
                subtask == null ? null : subtask.getId(),
                toolkit.getName(),
                blank(toolkit.getCenter()),
                blank(toolkit.getDomain()),
                blank(toolkit.getPl1()),
                blank(toolkit.getPl2()),
                blank(toolkit.getPl3Name()),
                blank(toolkit.getPrimaryPl3Code()),
                distinctValues(selections.stream().map(ToolkitSharedKpiSelection::getCarrier).toList()),
                distinctValues(selections.stream().map(ToolkitSharedKpiSelection::getSite).toList()),
                CommaTokens.distinct(
                        selections.stream().map(ToolkitSharedKpiSelection::getCustomerCountry).toList()),
                subtask == null ? "—" : subtask.getName(),
                resolvedAgent,
                session.getAgentCcgid(),
                session.getProcessedVolume(),
                session.getReference(),
                session.getRemarks(),
                session.getStatus().name().toLowerCase(Locale.ROOT),
                session.isEnabled(),
                wireStartedAt,
                session.getPausedAt(),
                session.getEndedAt(),
                wireNetDuration,
                session.getDiscardReason(),
                session.getVersion());
    }

    private static List<String> distinctValues(List<String> values) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                seen.add(value.trim());
            }
        }
        return List.copyOf(new ArrayList<>(seen));
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
