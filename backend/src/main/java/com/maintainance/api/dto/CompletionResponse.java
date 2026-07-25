package com.maintainance.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CompletionResponse(
        UUID id,
        UUID taskId,
        LocalDate scheduledAt,
        LocalDate plannedAt,
        Instant completedAt
) {
}
