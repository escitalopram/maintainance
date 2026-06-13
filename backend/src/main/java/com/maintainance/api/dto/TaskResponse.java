package com.maintainance.api.dto;

import com.maintainance.domain.model.OpenInstance;
import com.maintainance.domain.model.TaskRules;

import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
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
}
