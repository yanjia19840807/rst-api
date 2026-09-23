package com.cmacgm.gbs.rst.api.tms.application;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.cmacgm.gbs.rst.api.common.time.CenterZones;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cmacgm.gbs.rst.api.common.excel.ExcelSheets;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSessionResponse;

/**
 * Excel export for filtered TMS session lists.
 */
@Component
public class TmsSessionExcelService {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> HEADERS = List.of(
            "Session No",
            "Created by",
            "Toolkit",
            "GBS Center",
            "Domain",
            "PL1",
            "PL2",
            "PL3",
            "Carrier",
            "GBS Site",
            "Customer Country",
            "Subtask",
            "Start",
            "End",
            "Duration",
            "Cycle Time",
            "Reference",
            "Volume",
            "Remarks",
            "Status");

    /**
     * Writes the current filtered session list.
     *
     * @param sessions filtered sessions in list order
     * @return xlsx bytes
     */
    public byte[] export(List<TmsSessionResponse> sessions) {
        List<List<String>> body = new ArrayList<>();
        for (TmsSessionResponse session : sessions) {
            body.add(List.of(
                    blank(session.id()),
                    blank(session.agentName()),
                    blank(session.toolkitName()),
                    blank(session.center()),
                    blank(session.domain()),
                    blank(session.pl1()),
                    blank(session.pl2()),
                    blank(session.pl3()),
                    joined(session.carriers()),
                    joined(session.sites()),
                    joined(session.customerCountries()),
                    dash(session.subtaskName()),
                    formatInstant(session.startedAt(), session.center()),
                    formatInstant(session.endedAt(), session.center()),
                    formatDuration(session.netDurationSeconds()),
                    cycleTime(session),
                    dash(session.reference()),
                    formatVolume(session.processedVolume()),
                    dash(session.remarks()),
                    session.enabled() ? "Enabled" : "Disabled"));
        }
        return ExcelSheets.write("TMS Sessions", HEADERS, body);
    }

    private static String cycleTime(TmsSessionResponse session) {
        BigDecimal volume = session.processedVolume();
        if (volume == null || volume.signum() <= 0) {
            return "";
        }
        return String.valueOf(Math.round(session.netDurationSeconds() / volume.doubleValue()));
    }

    private static String formatVolume(BigDecimal volume) {
        return volume == null ? "" : volume.stripTrailingZeros().toPlainString();
    }

    private static String formatInstant(java.time.Instant value, String center) {
        if (value == null) {
            return "";
        }
        ZoneId zone = CenterZones.of(center);
        return DATE_TIME.format(value.atZone(zone));
    }

    private static String formatDuration(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, remainder);
    }

    private static String joined(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(", ", values);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String dash(String value) {
        return value == null || value.isBlank() || "—".equals(value) ? "" : value;
    }
}
