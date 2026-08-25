package com.cmacgm.gbs.rst.api.toolkit.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.time.MonthKeys;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitVolumePointsView;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitVolumePointsView.ToolkitDailyPointView;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitVolumePointsView.ToolkitMonthlyPointView;
import com.cmacgm.gbs.rst.api.toolkit.api.dto.ToolkitVolumeSummaryView;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitVolumeDaily;
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitVolumeMonthly;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitVolumeDailyRepository;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitVolumeMonthlyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canonical Toolkit volume series. Exercise overlay and approval freeze live in
 * {@code ExerciseVolumeTrainingService}.
 */
@Service
public class ToolkitVolumeService {

    public static final String SOURCE_EXERCISE = "EXERCISE";
    public static final String SOURCE_TOOLKIT = "TOOLKIT";
    public static final String GRAIN_MONTH = "MONTH";
    public static final String GRAIN_DAY = "DAY";

    private final ToolkitVolumeMonthlyRepository toolkitMonthly;
    private final ToolkitVolumeDailyRepository toolkitDaily;

    public ToolkitVolumeService(
            ToolkitVolumeMonthlyRepository toolkitMonthly,
            ToolkitVolumeDailyRepository toolkitDaily) {
        this.toolkitMonthly = toolkitMonthly;
        this.toolkitDaily = toolkitDaily;
    }

    /**
     * Canonical monthly rows for a Toolkit, oldest first.
     */
    @Transactional(readOnly = true)
    public List<ToolkitVolumeMonthly> listMonthly(UUID toolkitId) {
        return toolkitMonthly.findByToolkitIdOrderByMonthAsc(toolkitId);
    }

    /**
     * Canonical daily rows for a Toolkit, oldest first.
     */
    @Transactional(readOnly = true)
    public List<ToolkitVolumeDaily> listDaily(UUID toolkitId) {
        return toolkitDaily.findByToolkitIdOrderByVolumeDateAsc(toolkitId);
    }

    /**
     * Lookup map for pre-filling Volume Input from the canonical series.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, BigDecimal> monthlySeedByMonth(UUID toolkitId) {
        Map<LocalDate, BigDecimal> out = new LinkedHashMap<>();
        for (ToolkitVolumeMonthly row : listMonthly(toolkitId)) {
            if (row.getActualVolume() != null) {
                out.put(row.getMonth(), row.getActualVolume());
            }
        }
        return out;
    }

    /**
     * Lookup map for pre-filling daily Volume Input from the canonical series.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, BigDecimal> dailySeedByDate(UUID toolkitId) {
        Map<LocalDate, BigDecimal> out = new LinkedHashMap<>();
        for (ToolkitVolumeDaily row : listDaily(toolkitId)) {
            if (row.getActualVolume() != null) {
                out.put(row.getVolumeDate(), row.getActualVolume());
            }
        }
        return out;
    }

    /**
     * Summary of the canonical Toolkit series (for the Volume editor hint).
     */
    @Transactional(readOnly = true)
    public ToolkitVolumeSummaryView summarize(UUID toolkitId) {
        List<ToolkitVolumeMonthly> months = listMonthly(toolkitId);
        List<ToolkitVolumeDaily> days = listDaily(toolkitId);
        return new ToolkitVolumeSummaryView(
                months.size(),
                months.isEmpty() ? null : MonthKeys.formatYearMonth(months.getFirst().getMonth()),
                months.isEmpty() ? null : MonthKeys.formatYearMonth(months.getLast().getMonth()),
                days.size(),
                days.isEmpty() ? null : days.getFirst().getVolumeDate(),
                days.isEmpty() ? null : days.getLast().getVolumeDate());
    }

    /**
     * Non-null canonical actuals for add-row / import pre-fill.
     */
    @Transactional(readOnly = true)
    public ToolkitVolumePointsView listPoints(UUID toolkitId) {
        List<ToolkitMonthlyPointView> months = new ArrayList<>();
        for (ToolkitVolumeMonthly row : listMonthly(toolkitId)) {
            if (row.getActualVolume() != null) {
                months.add(new ToolkitMonthlyPointView(
                        MonthKeys.formatYearMonth(row.getMonth()),
                        row.getActualVolume()));
            }
        }
        List<ToolkitDailyPointView> days = new ArrayList<>();
        for (ToolkitVolumeDaily row : listDaily(toolkitId)) {
            if (row.getActualVolume() != null) {
                days.add(new ToolkitDailyPointView(row.getVolumeDate(), row.getActualVolume()));
            }
        }
        return new ToolkitVolumePointsView(months, days);
    }

    /**
     * Upserts one canonical monthly actual from an approved Exercise.
     */
    @Transactional
    public void upsertMonthly(
            UUID toolkitId,
            LocalDate month,
            BigDecimal actualVolume,
            UUID sourceExerciseId,
            String actorCcgid,
            Instant now) {
        toolkitMonthly.findByToolkitIdAndMonth(toolkitId, month)
                .ifPresentOrElse(
                        existing -> existing.replaceFrom(actualVolume, sourceExerciseId, actorCcgid, now),
                        () -> toolkitMonthly.save(ToolkitVolumeMonthly.create(
                                toolkitId, month, actualVolume, sourceExerciseId, actorCcgid, now)));
    }

    /**
     * Upserts one canonical daily actual from an approved Exercise.
     */
    @Transactional
    public void upsertDaily(
            UUID toolkitId,
            LocalDate volumeDate,
            BigDecimal actualVolume,
            UUID sourceExerciseId,
            String actorCcgid,
            Instant now) {
        toolkitDaily.findByToolkitIdAndVolumeDate(toolkitId, volumeDate)
                .ifPresentOrElse(
                        existing -> existing.replaceFrom(actualVolume, sourceExerciseId, actorCcgid, now),
                        () -> toolkitDaily.save(ToolkitVolumeDaily.create(
                                toolkitId, volumeDate, actualVolume, sourceExerciseId, actorCcgid, now)));
    }

    /**
     * SHA-256 of the actual series (period + volume). Source is not part of the hash.
     */
    public static String hashPoints(List<TrainingPoint> points) {
        StringBuilder raw = new StringBuilder();
        for (TrainingPoint point : points) {
            raw.append(point.periodStart())
                    .append('|')
                    .append(scale(point.actualVolume()).toPlainString())
                    .append('\n');
        }
        return sha256Hex(raw.toString());
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /**
     * One actual used as SARIMAX history, with provenance.
     */
    public record TrainingPoint(
            LocalDate periodStart,
            BigDecimal actualVolume,
            String source,
            UUID sourceExerciseId) {
    }
}
