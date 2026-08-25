package com.cmacgm.gbs.rst.api.exercise.scenario.application.sizing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Workbook §11.2 Volume-per-Slot formulas plus FIFO SLA aging.
 */
public final class SlotMath {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private SlotMath() {
    }

    /** Slot manual volume: Raw × (1 − Automation). */
    public static BigDecimal manualVolume(BigDecimal rawVolume, BigDecimal automationRatio) {
        return nz(rawVolume)
                .multiply(BigDecimal.ONE.subtract(nz(automationRatio)), MC)
                .setScale(6, RoundingMode.HALF_UP);
    }

    /** Cases per FTE capacity for one slot. */
    public static BigDecimal casesPerFte(
            int slotMinutes, BigDecimal cycleTimeSeconds, BigDecimal availabilityRatio) {
        if (slotMinutes <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal cycle = nz(cycleTimeSeconds);
        if (cycle.signum() <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(slotMinutes)
                .multiply(new BigDecimal("60"), MC)
                .divide(cycle, MC)
                .multiply(nz(availabilityRatio), MC)
                .setScale(6, RoundingMode.HALF_UP);
    }

    /** Theoretical FTE needed for the slot manual volume. */
    public static BigDecimal theoreticalFte(BigDecimal manualVolume, BigDecimal casesPerFte) {
        BigDecimal cpf = nz(casesPerFte);
        if (cpf.signum() <= 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return nz(manualVolume).divide(cpf, MC).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Whether a shift covers the slot local-time window.
     * Shift window is {@code [start, start + duration)} and may wrap past midnight.
     * Fractional minutes are rounded to the nearest whole minute for overlap.
     */
    public static boolean withinShift(
            LocalTime slotStartLocal,
            LocalTime slotEndLocal,
            LocalTime shiftStart,
            BigDecimal durationMinutes) {
        if (durationMinutes == null || durationMinutes.signum() <= 0) {
            return false;
        }
        int minutes = durationMinutes.setScale(0, RoundingMode.HALF_UP).intValue();
        return withinShift(slotStartLocal, slotEndLocal, shiftStart, minutes);
    }

    /**
     * Whether a shift covers the slot local-time window.
     * Shift window is {@code [start, start + duration)} and may wrap past midnight.
     */
    public static boolean withinShift(
            LocalTime slotStartLocal,
            LocalTime slotEndLocal,
            LocalTime shiftStart,
            int durationMinutes) {
        if (shiftStart == null || durationMinutes <= 0 || slotStartLocal == null || slotEndLocal == null) {
            return false;
        }
        int slotStart = minutesOfDay(slotStartLocal);
        int slotEnd = minutesOfDay(slotEndLocal);
        if (slotEnd <= slotStart) {
            slotEnd += 24 * 60;
        }
        int shiftFrom = minutesOfDay(shiftStart);
        int shiftTo = shiftFrom + durationMinutes;
        return rangesOverlap(slotStart, slotEnd, shiftFrom, shiftTo)
                || rangesOverlap(slotStart + 24 * 60, slotEnd + 24 * 60, shiftFrom, shiftTo)
                || rangesOverlap(slotStart, slotEnd, shiftFrom - 24 * 60, shiftTo - 24 * 60);
    }

    /** Headcount contributed by one shift when it covers the slot (else 0). */
    public static BigDecimal shiftContribution(
            boolean weekendDay,
            boolean worksOnWeekend,
            boolean coversSlot,
            BigDecimal headcount) {
        if (!coversSlot) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        if (weekendDay && !worksOnWeekend) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return nz(headcount).setScale(6, RoundingMode.HALF_UP);
    }

    /** Team capacity = sum(ShiftFTE) × casesPerFte. */
    public static BigDecimal teamCapacity(BigDecimal shiftFteTotal, BigDecimal casesPerFte) {
        return nz(shiftFteTotal).multiply(nz(casesPerFte), MC).setScale(6, RoundingMode.HALF_UP);
    }

    /** Backlog end for the slot. */
    public static BigDecimal backlogEnd(
            BigDecimal backlogStart, BigDecimal manualVolume, BigDecimal teamCapacity) {
        BigDecimal end = nz(backlogStart).add(nz(manualVolume), MC).subtract(nz(teamCapacity), MC);
        if (end.signum() < 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        return end.setScale(6, RoundingMode.HALF_UP);
    }

    /** Cumulative TAT = max(0, 1 − outOfSla / manual). */
    public static BigDecimal tat(BigDecimal volumeOutsideSlaSum, BigDecimal manualVolumeSum) {
        BigDecimal manual = nz(manualVolumeSum);
        if (manual.signum() <= 0) {
            return BigDecimal.ONE.setScale(8, RoundingMode.HALF_UP);
        }
        BigDecimal ratio = nz(volumeOutsideSlaSum).divide(manual, MC);
        BigDecimal tat = BigDecimal.ONE.subtract(ratio, MC);
        if (tat.signum() < 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return tat.setScale(8, RoundingMode.HALF_UP);
    }

    /** Actual vs theoretical = sum(capacity) / sum(manual). */
    public static BigDecimal actualVsTheoretical(
            BigDecimal teamCapacitySum, BigDecimal manualVolumeSum) {
        BigDecimal manual = nz(manualVolumeSum);
        if (manual.signum() <= 0) {
            return BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP);
        }
        return nz(teamCapacitySum).divide(manual, MC).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Applicability On when calendar SLA ≤ 24h, or business-hours SLA ≤ 8h.
     */
    public static boolean applicabilityOn(String slaType, BigDecimal slaTurnaroundMinutes) {
        if (slaTurnaroundMinutes == null || slaTurnaroundMinutes.signum() <= 0) {
            return false;
        }
        String type = slaType == null ? "" : slaType.trim().toUpperCase();
        boolean businessHours = type.contains("BUSINESS") || type.contains("BH");
        BigDecimal limit = BigDecimal.valueOf(businessHours ? 8 * 60 : 24 * 60);
        return slaTurnaroundMinutes.compareTo(limit) <= 0;
    }

    /** Number of 30-min (or slot) steps allowed before volume ages out of SLA. */
    public static int slaSlotLimit(BigDecimal slaTurnaroundMinutes, int slotMinutes) {
        if (slaTurnaroundMinutes == null || slaTurnaroundMinutes.signum() <= 0 || slotMinutes <= 0) {
            return 1;
        }
        return Math.max(1, slaTurnaroundMinutes
                .divide(BigDecimal.valueOf(slotMinutes), 8, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.CEILING)
                .intValue());
    }

    /**
     * FIFO volume queue that ages cohorts by slot index and emits VolumeOutOfSLA.
     */
    public static final class SlaQueue {
        private final int slaSlots;
        private final Deque<Cohort> cohorts = new ArrayDeque<>();

        public SlaQueue(int slaSlots) {
            this.slaSlots = Math.max(1, slaSlots);
        }

        /**
         * Processes one slot: expire aged volume, enqueue arrivals, consume capacity FIFO.
         *
         * @return volume that aged out of SLA this slot
         */
        public BigDecimal processSlot(int slotIndex, BigDecimal arrival, BigDecimal capacity) {
            BigDecimal outside = BigDecimal.ZERO;
            while (!cohorts.isEmpty()) {
                Cohort head = cohorts.peekFirst();
                if (slotIndex - head.arrivedSlotIndex < slaSlots) {
                    break;
                }
                outside = outside.add(head.remaining, MC);
                cohorts.removeFirst();
            }

            BigDecimal incoming = nz(arrival);
            if (incoming.signum() > 0) {
                cohorts.addLast(new Cohort(slotIndex, incoming));
            }

            BigDecimal remainingCapacity = nz(capacity);
            while (remainingCapacity.signum() > 0 && !cohorts.isEmpty()) {
                Cohort head = cohorts.peekFirst();
                if (head.remaining.compareTo(remainingCapacity) <= 0) {
                    remainingCapacity = remainingCapacity.subtract(head.remaining, MC);
                    cohorts.removeFirst();
                } else {
                    head.remaining = head.remaining.subtract(remainingCapacity, MC);
                    remainingCapacity = BigDecimal.ZERO;
                }
            }
            return outside.setScale(6, RoundingMode.HALF_UP);
        }
    }

    private static final class Cohort {
        private final int arrivedSlotIndex;
        private BigDecimal remaining;

        private Cohort(int arrivedSlotIndex, BigDecimal remaining) {
            this.arrivedSlotIndex = arrivedSlotIndex;
            this.remaining = remaining;
        }
    }

    private static boolean rangesOverlap(int aStart, int aEnd, int bStart, int bEnd) {
        return aStart < bEnd && bStart < aEnd;
    }

    private static int minutesOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
