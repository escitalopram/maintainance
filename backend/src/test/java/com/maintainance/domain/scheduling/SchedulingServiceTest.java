package com.maintainance.domain.scheduling;

import com.maintainance.domain.model.AnchorMode;
import com.maintainance.domain.model.IntervalType;
import com.maintainance.domain.model.TaskRules;
import com.maintainance.domain.model.TaskState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SchedulingServiceTest {

    private final SchedulingService schedulingService =
            new SchedulingService((scriptPath, scriptArgs) -> false);

    @Test
    void catchUpIncrementsWhenPredecessorSuperseded() {
        LocalDate epoch = LocalDate.of(2026, 5, 1);
        LocalDate today = LocalDate.of(2026, 5, 5);
        LocalDate watermark = LocalDate.of(2026, 4, 30);
        TaskState task = dailyCatchUpTask(epoch, epoch, 0, null, watermark);

        TaskState afterFirst = schedulingService.reconcileOnRead(task, today);
        assertEquals(3, afterFirst.catchUpCount());
        assertEquals(LocalDate.of(2026, 5, 3), afterFirst.lastMissedScheduledAt());
        assertEquals(today, afterFirst.lastReconciledDate());

        TaskState afterSecond = schedulingService.reconcileOnRead(afterFirst, today);
        assertEquals(3, afterSecond.catchUpCount());
    }

    @Test
    void markDoneDecrementSurvivesReconcileSameDay() {
        LocalDate epoch = LocalDate.of(2026, 5, 1);
        LocalDate today = LocalDate.of(2026, 5, 5);
        TaskState task = dailyCatchUpTask(epoch, epoch, 3, LocalDate.of(2026, 5, 3), today);

        TaskState decremented = task.withSchedulingFields(
                epoch, LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 3), 2, today, null);
        TaskState afterReconcile = schedulingService.reconcileOnRead(decremented, today);

        assertEquals(2, afterReconcile.catchUpCount());
        assertEquals(LocalDate.of(2026, 5, 3), afterReconcile.lastMissedScheduledAt());
    }

    @Test
    void clearingBacklogClearsLastMissed() {
        LocalDate epoch = LocalDate.of(2026, 5, 1);
        LocalDate today = LocalDate.of(2026, 5, 5);
        TaskState task = dailyCatchUpTask(epoch, epoch, 1, LocalDate.of(2026, 5, 3), today);

        TaskState cleared = task.withSchedulingFields(
                epoch, LocalDate.of(2026, 5, 5), null, 0, today, null);
        TaskState afterReconcile = schedulingService.reconcileOnRead(cleared, today);

        assertEquals(0, afterReconcile.catchUpCount());
        assertNull(afterReconcile.lastMissedScheduledAt());
    }

    @Test
    void wednesdayLeavesTuesdayAsLastPastCurrentWithOneMissed() {
        LocalDate epoch = LocalDate.of(2026, 5, 1);
        LocalDate today = LocalDate.of(2026, 5, 3);
        TaskState task = dailyCatchUpTask(epoch, epoch, 0, null, LocalDate.of(2026, 4, 30));

        TaskState reconciled = schedulingService.reconcileOnRead(task, today);
        assertEquals(1, reconciled.catchUpCount());
        assertEquals(LocalDate.of(2026, 5, 1), reconciled.lastMissedScheduledAt());
        assertEquals(LocalDate.of(2026, 5, 2), IntervalGrid.lastPastCurrentObligation(epoch, task.rules(), today));
    }

    @Test
    void horizonAssumedCompletedDatesCoverGapOnly() {
        LocalDate epoch = LocalDate.of(2026, 5, 1);
        LocalDate today = LocalDate.of(2026, 5, 27);
        LocalDate horizonStart = LocalDate.of(2026, 6, 1);
        TaskState task = dailyCatchUpTask(epoch, LocalDate.of(2026, 6, 1), 0, null, today);

        Set<LocalDate> assumed = schedulingService.horizonAssumedCompletedDates(task, today, horizonStart);

        assertEquals(5, assumed.size());
        assertEquals(true, assumed.contains(LocalDate.of(2026, 5, 27)));
        assertEquals(true, assumed.contains(LocalDate.of(2026, 5, 31)));
        assertEquals(false, assumed.contains(LocalDate.of(2026, 5, 26)));
        assertEquals(false, assumed.contains(LocalDate.of(2026, 6, 1)));
    }

    @Test
    void lastCompletionColdStartSchedulesFromCreatedDate() {
        LocalDate created = LocalDate.of(2026, 7, 25);
        TaskState task = lastCompletionTask(created, 21.0, created, null);

        LocalDate next = schedulingService.computeNextScheduled(task, created);
        assertEquals(LocalDate.of(2026, 8, 15), next);
    }

    @Test
    void lastCompletionSeptemberHorizonSkipsGapAssumptions() {
        LocalDate created = LocalDate.of(2026, 7, 25);
        LocalDate today = LocalDate.of(2026, 7, 25);
        LocalDate horizonStart = LocalDate.of(2026, 9, 1);
        LocalDate horizonEnd = LocalDate.of(2026, 9, 30);

        TaskState task21 = lastCompletionTask(created, 21.0, created, null);
        task21 = schedulingService.initializeEpoch(task21, today);
        Set<LocalDate> assumed21 = schedulingService.horizonAssumedCompletedDates(task21, today, horizonStart);
        var instances21 = schedulingService.scheduleHorizon(
                task21, horizonStart, horizonEnd, today, assumed21);

        assertEquals(
                Set.of(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 26)),
                Set.copyOf(instances21.stream().map(i -> i.scheduledAt()).toList()));

        TaskState task42 = lastCompletionTask(created, 42.0, created, null);
        task42 = schedulingService.initializeEpoch(task42, today);
        Set<LocalDate> assumed42 = schedulingService.horizonAssumedCompletedDates(task42, today, horizonStart);
        var instances42 = schedulingService.scheduleHorizon(
                task42, horizonStart, horizonEnd, today, assumed42);

        assertEquals(
                Set.of(LocalDate.of(2026, 9, 5)),
                Set.copyOf(instances42.stream().map(i -> i.scheduledAt()).toList()));
    }

    @Test
    void scheduleHorizonIncludesLastPastCurrent() {
        LocalDate epoch = LocalDate.of(2026, 5, 1);
        LocalDate today = LocalDate.of(2026, 5, 3);
        TaskState task = dailyCatchUpTask(epoch, LocalDate.of(2026, 5, 3), 1,
                LocalDate.of(2026, 5, 1), today);

        var instances = schedulingService.scheduleHorizon(
                task, today, today.plusDays(7), today, Set.of());

        long currentCount = instances.stream()
                .filter(i -> i.scheduledAt().equals(LocalDate.of(2026, 5, 2)))
                .count();
        assertEquals(1, currentCount);
    }

    private static TaskState lastCompletionTask(
            LocalDate createdDate,
            double intervalN,
            LocalDate epochStart,
            LocalDate nextScheduled
    ) {
        TaskRules rules = new TaskRules(
                IntervalType.EVERY_N_DAYS,
                intervalN,
                AnchorMode.LAST_COMPLETION,
                false,
                true,
                Set.of(),
                null,
                null,
                null,
                null,
                15,
                1.0,
                0,
                3,
                3.0,
                3.0,
                0.6,
                null,
                null
        );
        return new TaskState(
                UUID.randomUUID(),
                "test",
                null,
                false,
                rules,
                createdDate,
                null,
                epochStart,
                nextScheduled,
                null,
                0,
                createdDate,
                null
        );
    }

    private static TaskState dailyCatchUpTask(
            LocalDate epochStart,
            LocalDate nextScheduled,
            int catchUpCount,
            LocalDate lastMissed,
            LocalDate lastReconciled
    ) {
        TaskRules rules = new TaskRules(
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
        return new TaskState(
                UUID.randomUUID(),
                "test",
                null,
                false,
                rules,
                epochStart,
                null,
                epochStart,
                nextScheduled,
                lastMissed,
                catchUpCount,
                lastReconciled,
                null
        );
    }
}
