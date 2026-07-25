package com.maintainance.domain.scheduling;

import com.maintainance.domain.model.TaskRules;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

public final class RuleConstraints {

    private RuleConstraints() {
    }

    public static LocalDate applyWeekdaySnap(LocalDate candidate, TaskRules rules) {
        if (rules.allowedWeekdaysOrAll().contains(candidate.getDayOfWeek().getValue())) {
            return candidate;
        }
        for (int offset = 1; offset <= 7; offset++) {
            LocalDate before = candidate.minusDays(offset);
            LocalDate after = candidate.plusDays(offset);
            boolean beforeOk = rules.allowedWeekdaysOrAll().contains(before.getDayOfWeek().getValue());
            boolean afterOk = rules.allowedWeekdaysOrAll().contains(after.getDayOfWeek().getValue());
            if (beforeOk && afterOk) {
                long db = Math.abs(ChronoUnit.DAYS.between(candidate, before));
                long da = Math.abs(ChronoUnit.DAYS.between(candidate, after));
                return db <= da ? before : after;
            }
            if (beforeOk) {
                return before;
            }
            if (afterOk) {
                return after;
            }
        }
        return candidate;
    }

    public static LocalDate applySeasonalWindow(LocalDate candidate, TaskRules rules) {
        if (rules.seasonStart() == null || rules.seasonEnd() == null) {
            return candidate;
        }
        int year = candidate.getYear();
        LocalDate windowStart = rules.seasonStart().withYear(year);
        LocalDate windowEnd = rules.seasonEnd().withYear(year);
        if (!windowEnd.isBefore(windowStart)) {
            if (candidate.isBefore(windowStart)) {
                return windowStart;
            }
            if (candidate.isAfter(windowEnd)) {
                return windowEnd;
            }
            return candidate;
        }
        // Window wraps year end
        if (!candidate.isBefore(windowStart) || !candidate.isAfter(windowEnd)) {
            return candidate;
        }
        long toStart = Math.abs(ChronoUnit.DAYS.between(candidate, windowStart));
        long toEnd = Math.abs(ChronoUnit.DAYS.between(candidate, windowEnd));
        return toStart <= toEnd ? windowStart : windowEnd;
    }

    public static LocalDate applyMinGap(LocalDate candidate, LocalDate previousScheduled, TaskRules rules) {
        if (rules.minDaysBetweenScheduled() == null || previousScheduled == null) {
            return candidate;
        }
        LocalDate result = candidate;
        while (ChronoUnit.DAYS.between(previousScheduled, result) < rules.minDaysBetweenScheduled()) {
            result = result.plusDays(1);
        }
        return result;
    }

    public static LocalDate applyAllConstraints(
            LocalDate candidate,
            LocalDate previousScheduled,
            TaskRules rules
    ) {
        LocalDate d = applyWeekdaySnap(candidate, rules);
        d = applySeasonalWindow(d, rules);
        d = applyMinGap(d, previousScheduled, rules);
        return d;
    }

    public static boolean isArchived(TaskRules rules, LocalDate reference) {
        return rules.endDate() != null && reference.isAfter(rules.endDate());
    }

    public static LocalDate asap(LocalDate afterDate, TaskRules rules) {
        LocalDate start = afterDate == null ? LocalDate.now() : afterDate;
        if (!start.isAfter(LocalDate.now())) {
            start = LocalDate.now();
        }
        return applyAllConstraints(start, null, rules);
    }
}
