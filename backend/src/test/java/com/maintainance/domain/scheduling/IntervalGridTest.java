package com.maintainance.domain.scheduling;

import com.maintainance.domain.model.TaskRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalGridTest {

    @Test
    void everyOnePointThreeThreeDaysSkipsSomeCalendarDays() {
        TaskRules rules = new TaskRules(
                com.maintainance.domain.model.IntervalType.EVERY_N_DAYS,
                1.333,
                com.maintainance.domain.model.AnchorMode.EPOCH,
                true,
                true,
                java.util.Set.of(),
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
        LocalDate epoch = LocalDate.of(2026, 1, 1);
        java.util.Set<LocalDate> dates = new java.util.LinkedHashSet<>();
        LocalDate last = null;
        for (long k = 0; k < 20; k++) {
            LocalDate d = IntervalGrid.gridSlotDate(epoch, rules, k);
            if (last == null || !d.equals(last)) {
                dates.add(d);
                last = d;
            }
        }
        assertThat(dates).hasSizeGreaterThan(10);
        long spanDays = dates.stream().mapToLong(LocalDate::toEpochDay).max().getAsLong()
                - dates.stream().mapToLong(LocalDate::toEpochDay).min().getAsLong() + 1;
        assertThat((long) dates.size()).isLessThan(spanDays);
    }

    @Test
    void rejectsIntervalBelowOne() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                new TaskRules(
                        com.maintainance.domain.model.IntervalType.EVERY_N_DAYS,
                        0.5,
                        com.maintainance.domain.model.AnchorMode.EPOCH,
                        true,
                        true,
                        java.util.Set.of(),
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
                ));
    }
}
