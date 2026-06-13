package com.maintainance.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public record SchedulableInstance(
        String instanceKey,
        UUID taskId,
        UUID openInstanceId,
        LocalDate scheduledAt,
        LocalDate snoozeUntil,
        boolean virtual,
        boolean backlog,
        boolean overdue,
        TaskRules rules,
        int catchUpCountForTask
) {
    public boolean isBacklogVirtual() {
        return backlog;
    }
}
