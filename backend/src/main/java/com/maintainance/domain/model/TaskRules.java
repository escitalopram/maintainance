package com.maintainance.domain.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

public record TaskRules(
        IntervalType intervalType,
        double intervalN,
        AnchorMode anchorMode,
        boolean catchUp,
        boolean useBacklogMultiplier,
        Set<Integer> allowedWeekdays,
        LocalDate seasonStart,
        LocalDate seasonEnd,
        LocalDate endDate,
        Integer minDaysBetweenScheduled,
        int durationMinutes,
        double importanceWeight,
        int graceEarlyDays,
        int graceLateDays,
        double sigmaEarly,
        double sigmaLate,
        double backlogP,
        String dueScriptPath,
        String dueScriptArgs
) {
    public TaskRules {
        if (intervalN < 1.0 && intervalType != IntervalType.EXTERNAL_DUE) {
            throw new IllegalArgumentException("intervalN must be >= 1");
        }
        if (importanceWeight < 1.0) {
            throw new IllegalArgumentException("importanceWeight must be >= 1");
        }
        allowedWeekdays = allowedWeekdays == null ? Set.of() : Set.copyOf(allowedWeekdays);
    }

    public static TaskRules defaultsDaily() {
        return new TaskRules(
                IntervalType.EVERY_N_DAYS,
                1.0,
                AnchorMode.EPOCH,
                true,
                true,
                Set.of(),
                null,
                null,
                null,
                null,
                15,
                1.0,
                0,
                0,
                3.0,
                3.0,
                0.6,
                null,
                null
        );
    }

    public boolean isExternalDue() {
        return intervalType == IntervalType.EXTERNAL_DUE;
    }

    public Set<Integer> allowedWeekdaysOrAll() {
        if (allowedWeekdays.isEmpty()) {
            return Set.of(1, 2, 3, 4, 5, 6, 7);
        }
        return allowedWeekdays;
    }
}
