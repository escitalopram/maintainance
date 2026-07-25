package com.maintainance.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public record TaskState(
        UUID id,
        String name,
        String description,
        boolean archived,
        TaskRules rules,
        LocalDate createdDate,
        LocalDate lastCompletionDate,
        LocalDate epochStart,
        LocalDate nextScheduled,
        LocalDate lastMissedScheduledAt,
        int catchUpCount,
        LocalDate lastReconciledDate,
        OpenInstance openInstance
) {
    public TaskState withOpenInstance(OpenInstance open) {
        return new TaskState(id, name, description, archived, rules, createdDate, lastCompletionDate,
                epochStart, nextScheduled, lastMissedScheduledAt, catchUpCount, lastReconciledDate, open);
    }

    public TaskState withSchedulingFields(
            LocalDate epochStart,
            LocalDate nextScheduled,
            LocalDate lastMissedScheduledAt,
            int catchUpCount,
            LocalDate lastReconciledDate,
            OpenInstance openInstance
    ) {
        return new TaskState(id, name, description, archived, rules, createdDate, lastCompletionDate,
                epochStart, nextScheduled, lastMissedScheduledAt, catchUpCount, lastReconciledDate, openInstance);
    }

    public TaskState withLastCompletionDate(LocalDate lastCompletionDate) {
        return new TaskState(id, name, description, archived, rules, createdDate, lastCompletionDate,
                epochStart, nextScheduled, lastMissedScheduledAt, catchUpCount, lastReconciledDate, openInstance);
    }
}
