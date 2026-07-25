package com.maintainance.domain.scheduling;

import com.maintainance.domain.model.TaskRules;

import java.time.LocalDate;

public final class GraceRules {

    private GraceRules() {
    }

    public static boolean inGraceAt(LocalDate referenceDay, LocalDate scheduledAt, TaskRules rules) {
        long delta = referenceDay.toEpochDay() - scheduledAt.toEpochDay();
        return delta >= -rules.graceEarlyDays() && delta <= rules.graceLateDays();
    }

    public static boolean isOverdueAtHorizon(
            LocalDate scheduledAt,
            LocalDate horizonStart,
            TaskRules rules,
            boolean completed
    ) {
        if (completed) {
            return false;
        }
        if (!scheduledAt.isBefore(horizonStart)) {
            return false;
        }
        return !inGraceAt(horizonStart, scheduledAt, rules);
    }
}
