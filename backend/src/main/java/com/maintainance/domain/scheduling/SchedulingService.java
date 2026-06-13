package com.maintainance.domain.scheduling;

import com.maintainance.domain.model.AnchorMode;
import com.maintainance.domain.model.OpenInstance;
import com.maintainance.domain.model.SchedulableInstance;
import com.maintainance.domain.model.TaskRules;
import com.maintainance.domain.model.TaskState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SchedulingService {

    private final DueFunctionEvaluator dueFunctionEvaluator;

    public SchedulingService(DueFunctionEvaluator dueFunctionEvaluator) {
        this.dueFunctionEvaluator = dueFunctionEvaluator;
    }

    public TaskState reconcileOnRead(TaskState task, LocalDate today) {
        if (task.archived() && task.catchUpCount() == 0 && task.openInstance() == null) {
            return advanceReconcileWatermark(task, today);
        }
        TaskRules rules = task.rules();
        if (rules.isExternalDue()) {
            return reconcileExternal(task, today);
        }

        TaskState current = task;
        if (rules.catchUp()) {
            current = reconcileCatchUpYes(current, today);
        } else {
            current = reconcileCatchUpNo(current, today);
            current = advanceReconcileWatermark(current, today);
        }
        if (!RuleConstraints.isArchived(rules, today) || current.catchUpCount() > 0 || current.openInstance() != null) {
            current = ensureNextScheduled(current, today);
        }
        return current;
    }

    /**
     * Gap slots {@code [today, H_start)} assumed complete for one horizon projection.
     * Ephemeral only — never emitted in scheduleHorizon output (scheduling-model §8).
     */
    public Set<LocalDate> horizonAssumedCompletedDates(TaskState task, LocalDate today, LocalDate horizonStart) {
        if (!horizonStart.isAfter(today) || task.epochStart() == null || task.rules().isExternalDue()) {
            return Set.of();
        }
        Set<LocalDate> assumed = new HashSet<>();
        for (LocalDate slot : IntervalGrid.gridSlotsThrough(task.epochStart(), task.rules(), horizonStart.minusDays(1))) {
            if (!slot.isBefore(today) && slot.isBefore(horizonStart)) {
                assumed.add(slot);
            }
        }
        return assumed;
    }

    private TaskState advanceReconcileWatermark(TaskState task, LocalDate today) {
        LocalDate watermark = task.lastReconciledDate() != null ? task.lastReconciledDate() : today;
        if (watermark.isBefore(today) || task.lastReconciledDate() == null) {
            return task.withSchedulingFields(
                    task.epochStart(),
                    task.nextScheduled(),
                    task.lastMissedScheduledAt(),
                    task.catchUpCount(),
                    today,
                    task.openInstance()
            );
        }
        return task;
    }

    private TaskState reconcileExternal(TaskState task, LocalDate today) {
        boolean due = dueFunctionEvaluator.isDue(task.rules().dueScriptPath(), task.rules().dueScriptArgs());
        if (!due) {
            return advanceReconcileWatermark(task, today);
        }
        LocalDate scheduled = RuleConstraints.asap(today.minusDays(1), task.rules());
        OpenInstance open = task.openInstance();
        if (open == null) {
            open = new OpenInstance(UUID.randomUUID(), task.id(), scheduled, null);
        } else if (open.scheduledAt().isAfter(scheduled)) {
            open = new OpenInstance(open.id(), task.id(), scheduled, open.snoozeUntil());
        }
        return task.withSchedulingFields(task.epochStart(), scheduled, null, 0, today, open);
    }

    private TaskState reconcileCatchUpNo(TaskState task, LocalDate today) {
        LocalDate obligationDate = lastPastCurrentObligation(task, today);
        if (obligationDate == null) {
            return task;
        }
        if (!today.isAfter(obligationDate)) {
            return task;
        }
        OpenInstance open = task.openInstance();
        if (open == null) {
            open = new OpenInstance(UUID.randomUUID(), task.id(), obligationDate, null);
        } else if (!open.scheduledAt().equals(obligationDate)) {
            open = new OpenInstance(open.id(), task.id(), obligationDate, open.snoozeUntil());
        }
        return task.withOpenInstance(open);
    }

    private TaskState reconcileCatchUpYes(TaskState task, LocalDate today) {
        LocalDate watermark = task.lastReconciledDate() != null ? task.lastReconciledDate() : today;
        int count = task.catchUpCount();
        LocalDate lastMissed = task.lastMissedScheduledAt();
        LocalDate epoch = task.epochStart();

        if (epoch != null && watermark.isBefore(today)) {
            for (LocalDate slot : IntervalGrid.gridSlotsThrough(epoch, task.rules(), today.minusDays(1))) {
                if (slot.isAfter(watermark) && slot.isBefore(today)) {
                    LocalDate predecessor = IntervalGrid.previousGridSlotBefore(epoch, task.rules(), slot);
                    if (predecessor != null && predecessor.isBefore(today)) {
                        count++;
                        lastMissed = predecessor;
                    }
                }
            }
        }

        return task.withSchedulingFields(
                task.epochStart(),
                task.nextScheduled(),
                lastMissed,
                count,
                today,
                task.openInstance()
        );
    }

    private LocalDate lastPastCurrentObligation(TaskState task, LocalDate today) {
        if (task.openInstance() != null) {
            return task.openInstance().scheduledAt();
        }
        return IntervalGrid.lastPastCurrentObligation(task.epochStart(), task.rules(), today);
    }

    public TaskState ensureNextScheduled(TaskState task, LocalDate today) {
        if (task.rules().isExternalDue()) {
            return task;
        }
        if (RuleConstraints.isArchived(task.rules(), today) && task.catchUpCount() == 0) {
            return task.withSchedulingFields(task.epochStart(), null, task.lastMissedScheduledAt(),
                    task.catchUpCount(), task.lastReconciledDate(), task.openInstance());
        }
        LocalDate next = computeNextScheduled(task, today);
        return task.withSchedulingFields(task.epochStart(), next, task.lastMissedScheduledAt(),
                task.catchUpCount(), task.lastReconciledDate(), task.openInstance());
    }

    public LocalDate computeNextScheduled(TaskState task, LocalDate afterDate) {
        TaskRules rules = task.rules();
        if (rules.isExternalDue()) {
            return task.nextScheduled();
        }
        LocalDate baseAfter = afterDate;
        if (rules.anchorMode() == AnchorMode.LAST_COMPLETION) {
            // Caller should pass appropriate afterDate from last completion when recomputing
        }
        LocalDate epoch = task.epochStart();
        if (epoch == null) {
            epoch = RuleConstraints.asap(afterDate, rules);
        }
        LocalDate previous = task.openInstance() != null
                ? task.openInstance().scheduledAt()
                : (task.nextScheduled() != null ? task.nextScheduled() : epoch);
        LocalDate candidate = IntervalGrid.nextGridSlotOnOrAfter(epoch, rules, baseAfter);
        candidate = RuleConstraints.applyAllConstraints(candidate, previous, rules);
        if (RuleConstraints.isArchived(rules, candidate)) {
            return null;
        }
        return candidate;
    }

    public List<SchedulableInstance> scheduleHorizon(
            TaskState task,
            LocalDate horizonStart,
            LocalDate horizonEnd,
            LocalDate today,
            Set<LocalDate> assumedCompletedDates
    ) {
        List<SchedulableInstance> result = new ArrayList<>();
        TaskRules rules = task.rules();

        if (rules.catchUp() && task.catchUpCount() > 0 && task.lastMissedScheduledAt() != null) {
            LocalDate snooze = task.openInstance() != null ? task.openInstance().snoozeUntil() : null;
            for (int i = 0; i < task.catchUpCount(); i++) {
                result.add(buildInstance(task, "backlog:" + i, task.lastMissedScheduledAt(), null,
                        true, true, horizonStart, snooze));
            }
        } else if (task.openInstance() != null) {
            OpenInstance open = task.openInstance();
            result.add(buildInstance(task, "open:" + open.id(), open.scheduledAt(), open.id(),
                    false, false, horizonStart, open.snoozeUntil()));
        }

        if (!rules.isExternalDue() && task.epochStart() != null) {
            LocalDate lastPastCurrent = IntervalGrid.lastPastCurrentObligation(
                    task.epochStart(), rules, today);
            if (lastPastCurrent != null && shouldIncludeLastPastCurrent(task, lastPastCurrent)) {
                LocalDate snooze = task.openInstance() != null ? task.openInstance().snoozeUntil() : null;
                result.add(buildInstance(task, "current:" + lastPastCurrent, lastPastCurrent, null,
                        true, false, horizonStart, snooze));
            }
        }

        if (rules.isExternalDue() || task.epochStart() == null) {
            return result;
        }

        LocalDate cursor = task.nextScheduled();
        if (cursor == null) {
            cursor = computeNextScheduled(task, horizonStart.minusDays(1));
        }
        if (cursor == null) {
            return result;
        }

        LocalDate previousScheduled = cursor;
        int forwardIndex = 0;
        while (cursor != null && !cursor.isAfter(horizonEnd)) {
            if (!cursor.isBefore(horizonStart)) {
                boolean duplicateOpen = task.openInstance() != null
                        && !rules.catchUp()
                        && task.openInstance().scheduledAt().equals(cursor);
                if (!duplicateOpen) {
                    LocalDate snooze = task.openInstance() != null ? task.openInstance().snoozeUntil() : null;
                    result.add(buildInstance(task, "forward:" + forwardIndex + ":" + cursor, cursor, null,
                            true, false, horizonStart, snooze));
                }
            }
            forwardIndex++;
            LocalDate nextRaw = IntervalGrid.nextGridSlotOnOrAfter(task.epochStart(), rules, cursor);
            if (!nextRaw.isAfter(cursor)) {
                nextRaw = cursor.plusDays(1);
            }
            cursor = RuleConstraints.applyAllConstraints(nextRaw, previousScheduled, rules);
            previousScheduled = cursor;
            if (RuleConstraints.isArchived(rules, cursor)) {
                break;
            }
        }

        return result;
    }

    private boolean shouldIncludeLastPastCurrent(TaskState task, LocalDate lastPastCurrent) {
        if (task.lastMissedScheduledAt() != null && task.lastMissedScheduledAt().equals(lastPastCurrent)) {
            return false;
        }
        if (task.openInstance() != null && task.openInstance().scheduledAt().equals(lastPastCurrent)) {
            return false;
        }
        return true;
    }

    private SchedulableInstance buildInstance(
            TaskState task,
            String keySuffix,
            LocalDate scheduledAt,
            UUID openId,
            boolean virtual,
            boolean backlog,
            LocalDate horizonStart,
            LocalDate snoozeUntil
    ) {
        boolean overdue = GraceRules.isOverdueAtHorizon(
                scheduledAt, horizonStart, task.rules(), false);
        return new SchedulableInstance(
                task.id() + ":" + keySuffix,
                task.id(),
                openId,
                scheduledAt,
                snoozeUntil,
                virtual,
                backlog,
                overdue,
                task.rules(),
                task.catchUpCount()
        );
    }

    public TaskState initializeEpoch(TaskState task, LocalDate today) {
        if (task.epochStart() != null) {
            return task;
        }
        LocalDate epoch = RuleConstraints.asap(today, task.rules());
        LocalDate next = RuleConstraints.applyAllConstraints(
                IntervalGrid.gridSlotDate(epoch, task.rules(), 0),
                null,
                task.rules()
        );
        return task.withSchedulingFields(epoch, next, null, 0, today, task.openInstance());
    }
}
