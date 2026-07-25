package com.maintainance.domain.planning;

import java.time.LocalDate;
import java.util.UUID;

public record PlanItem(
        String instanceKey,
        UUID taskId,
        LocalDate scheduledAt,
        LocalDate plannedAt,
        String placement,
        double timingPain,
        int durationMinutes,
        Integer withinDayOrder,
        boolean virtual,
        boolean backlog,
        boolean overdue
) {
}
