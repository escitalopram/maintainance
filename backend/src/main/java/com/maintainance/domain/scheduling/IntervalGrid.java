package com.maintainance.domain.scheduling;

import com.maintainance.domain.model.IntervalType;
import com.maintainance.domain.model.TaskRules;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class IntervalGrid {

    private static final double DAYS_PER_MONTH = 365.2425 / 12.0;
    private static final double DAYS_PER_YEAR = 365.2425;

    private IntervalGrid() {
    }

    public static double unitDays(IntervalType type) {
        return switch (type) {
            case EVERY_N_DAYS -> 1.0;
            case EVERY_N_WEEKS -> 7.0;
            case EVERY_N_MONTHS -> DAYS_PER_MONTH;
            case EVERY_N_YEARS -> DAYS_PER_YEAR;
            case EXTERNAL_DUE -> 1.0;
        };
    }

    public static double intervalDays(TaskRules rules) {
        return rules.intervalN() * unitDays(rules.intervalType());
    }

    public static LocalDate gridSlotDate(LocalDate epochStart, TaskRules rules, long k) {
        long epochIndex = epochStart.toEpochDay();
        double slotDayIndex = epochIndex + k * intervalDays(rules);
        long dayIndex = (long) Math.floor(slotDayIndex + 1e-9);
        return LocalDate.ofEpochDay(dayIndex);
    }

    public static long slotIndexForDate(LocalDate epochStart, TaskRules rules, LocalDate date) {
        double interval = intervalDays(rules);
        if (interval <= 0) {
            return 0;
        }
        double raw = (date.toEpochDay() - epochStart.toEpochDay()) / interval;
        return Math.max(0, (long) Math.floor(raw + 1e-9));
    }

    public static LocalDate nextGridSlotOnOrAfter(LocalDate epochStart, TaskRules rules, LocalDate afterExclusive) {
        long startK = 0;
        if (afterExclusive != null && !afterExclusive.isBefore(epochStart)) {
            startK = slotIndexForDate(epochStart, rules, afterExclusive);
            LocalDate candidate = gridSlotDate(epochStart, rules, startK);
            if (!candidate.isAfter(afterExclusive)) {
                startK++;
            }
        }
        for (long k = startK; k < startK + 10_000; k++) {
            LocalDate d = gridSlotDate(epochStart, rules, k);
            if (d.isAfter(afterExclusive == null ? LocalDate.MIN : afterExclusive)) {
                return d;
            }
        }
        throw new IllegalStateException("Could not find next grid slot");
    }

    public static Iterable<LocalDate> gridSlotsThrough(LocalDate epochStart, TaskRules rules, LocalDate throughInclusive) {
        return () -> new SlotIterator(epochStart, rules, throughInclusive);
    }

    private static final class SlotIterator implements java.util.Iterator<LocalDate> {
        private final LocalDate epochStart;
        private final TaskRules rules;
        private final LocalDate throughInclusive;
        private long k;
        private LocalDate next;
        private LocalDate lastEmitted;

        SlotIterator(LocalDate epochStart, TaskRules rules, LocalDate throughInclusive) {
            this.epochStart = epochStart;
            this.rules = rules;
            this.throughInclusive = throughInclusive;
            this.k = 0;
            advance();
        }

        private void advance() {
            next = null;
            while (k < 10_000) {
                LocalDate candidate = gridSlotDate(epochStart, rules, k++);
                if (candidate.isAfter(throughInclusive)) {
                    return;
                }
                if (lastEmitted == null || !candidate.equals(lastEmitted)) {
                    next = candidate;
                    return;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public LocalDate next() {
            if (next == null) {
                throw new java.util.NoSuchElementException();
            }
            LocalDate current = next;
            lastEmitted = current;
            advance();
            return current;
        }
    }

    public static long calendarDaysInclusive(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }
}
