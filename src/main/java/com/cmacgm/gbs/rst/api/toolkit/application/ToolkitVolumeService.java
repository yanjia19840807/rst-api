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
import com.cmacgm.gbs.rst.api.toolkit.domain.ToolkitVolumeSlot;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitVolumeDailyRepository;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitVolumeMonthlyRepository;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitVolumeSlotRepository;
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
    private final ToolkitVolumeSlotRepository toolkitSlot;

    public ToolkitVolumeService(
            ToolkitVolumeMonthlyRepository toolkitMonthly,
            ToolkitVolumeDailyRepository toolkitDaily,
            ToolkitVolumeSlotRepository toolkitSlot) {
        this.toolkitMonthly = toolkitMonthly;
        this.toolkitDaily = toolkitDaily;
        this.toolkitSlot = toolkitSlot;
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
     * Canonical slot rows for a Toolkit, oldest first.
     */
    @Transactional(readOnly = true)
    public List<ToolkitVolumeSlot> listSlot(UUID toolkitId) {
        return toolkitSlot.findByToolkitIdOrderBySlotStartAtAsc(toolkitId);
    }

    /**
     * Lookup map for pre-filling Volume Input from the canonical series.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, VolumeSeed> monthlySeedByMonth(UUID toolkitId) {
        Map<LocalDate, VolumeSeed> out = new LinkedHashMap<>();
        for (ToolkitVolumeMonthly row : listMonthly(toolkitId)) {
            if (row.getActualVolume() != null) {
                out.put(row.getMonth(), new VolumeSeed(row.getActualVolume(), row.getCommercialRatio()));
            }
        }
        return out;
    }

    /**
     * Lookup map for pre-filling daily Volume Input from the canonical series.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, VolumeSeed> dailySeedByDate(UUID toolkitId) {
        Map<LocalDate, VolumeSeed> out = new LinkedHashMap<>();
        for (ToolkitVolumeDaily row : listDaily(toolkitId)) {
            if (row.getActualVolume() != null) {
                out.put(row.getVolumeDate(), new VolumeSeed(row.getActualVolume(), row.getDailyAdjustmentRatio()));
            }
        }
        return out;
    }

    /**
     * Lookup map for overlapping slot actuals from the canonical series.
     */
    @Transactional(readOnly = true)
    public Map<Instant, BigDecimal> slotSeedByStart(UUID toolkitId) {
        Map<Instant, BigDecimal> out = new LinkedHashMap<>();
        for (ToolkitVolumeSlot row : listSlot(toolkitId)) {
            if (row.getActualVolume() != null) {
                out.put(row.getSlotStartAt(), row.getActualVolume());
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
            BigDecimal commercialRatio,
            UUID sourceExerciseId,
            String actorCcgid,
            Instant now) {
        toolkitMonthly.findByToolkitIdAndMonth(toolkitId, month)
                .ifPresentOrElse(
                        existing -> existing.replaceFrom(
                                actualVolume, commercialRatio, sourceExerciseId, actorCcgid, now),
                        () -> toolkitMonthly.save(ToolkitVolumeMonthly.create(
                                toolkitId, month, actualVolume, commercialRatio,
                                sourceExerciseId, actorCcgid, now)));
    }

    /**
     * Upserts one canonical daily actual from an approved Exercise.
     */
    @Transactional
    public void upsertDaily(
            UUID toolkitId,
            LocalDate volumeDate,
            BigDecimal actualVolume,
            BigDecimal dailyAdjustmentRatio,
            UUID sourceExerciseId,
            String actorCcgid,
            Instant now) {
        toolkitDaily.findByToolkitIdAndVolumeDate(toolkitId, volumeDate)
                .ifPresentOrElse(
                        existing -> existing.replaceFrom(
                                actualVolume, dailyAdjustmentRatio, sourceExerciseId, actorCcgid, now),
                        () -> toolkitDaily.save(ToolkitVolumeDaily.create(
                                toolkitId, volumeDate, actualVolume, dailyAdjustmentRatio,
                                sourceExerciseId, actorCcgid, now)));
    }

    /**
     * Upserts one canonical slot actual from an approved Exercise. Null actuals are skipped.
     */
    @Transactional
    public void upsertSlot(
            UUID toolkitId,
            Instant slotStartAt,
            Instant slotEndAt,
            BigDecimal actualVolume,
            UUID sourceExerciseId,
            String actorCcgid,
            Instant now) {
        if (actualVolume == null) {
            return;
        }
        toolkitSlot.findByToolkitIdAndSlotStartAt(toolkitId, slotStartAt)
                .ifPresentOrElse(
                        existing -> existing.replaceFrom(
                                slotEndAt, actualVolume, sourceExerciseId, actorCcgid, now),
                        () -> toolkitSlot.save(ToolkitVolumeSlot.create(
                                toolkitId, slotStartAt, slotEndAt, actualVolume,
                                sourceExerciseId, actorCcgid, now)));
    }

    /**
     * One Toolkit volume seed used to pre-fill an Exercise grid row.
     */
    public record VolumeSeed(BigDecimal actualVolume, BigDecimal ratio) {
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
