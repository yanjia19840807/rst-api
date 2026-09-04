package com.cmacgm.gbs.rst.api.tms.application;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
    private static final List<String> HEADERS = List.of(
            "Session No",
            "Agent",
            "Toolkit",
            "Subtask",
            "Start",
            "End",
            "Duration",
            "Cycle Time",
            "Reference",
            "Volume",
            "Remarks");

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
                    dash(session.subtaskName()),
                    formatInstant(session.startedAt()),
                    formatInstant(session.endedAt()),
                    formatDuration(session.netDurationSeconds()),
                    cycleTime(session),
                    dash(session.reference()),
                    formatVolume(session.processedVolume()),
                    dash(session.remarks())));
        }
        return ExcelSheets.write("TMS Sessions", HEADERS, body);
    }

    private static String cycleTime(TmsSessionResponse session) {
        BigDecimal volume = session.processedVolume();
        double divisor = volume == null || volume.signum() <= 0 ? 1.0 : volume.doubleValue();
        return String.valueOf(Math.round(session.netDurationSeconds() / divisor));
    }

    private static String formatVolume(BigDecimal volume) {
        return volume == null ? "" : volume.stripTrailingZeros().toPlainString();
    }

    private static String formatInstant(java.time.Instant value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    private static String formatDuration(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, remainder);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String dash(String value) {
        return value == null || value.isBlank() || "—".equals(value) ? "" : value;
    }
}
