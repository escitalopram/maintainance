package com.maintainance.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public record TaskState(
        UUID id,
        String name,
        String description,
        boolean archived,
        TaskRules rules,
        LocalDate epochStart,
        LocalDate nextScheduled,
        LocalDate lastMissedScheduledAt,
        int catchUpCount,
        OpenInstance openInstance
) {
    public TaskState withOpenInstance(OpenInstance open) {
        return new TaskState(id, name, description, archived, rules, epochStart, nextScheduled,
                lastMissedScheduledAt, catchUpCount, open);
    }

    public TaskState withSchedulingFields(
            LocalDate epochStart,
            LocalDate nextScheduled,
            LocalDate lastMissedScheduledAt,
            int catchUpCount,
            OpenInstance openInstance
    ) {
        return new TaskState(id, name, description, archived, rules, epochStart, nextScheduled,
                lastMissedScheduledAt, catchUpCount, openInstance);
    }
}
